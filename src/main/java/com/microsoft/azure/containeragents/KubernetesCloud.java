package com.microsoft.azure.containeragents;

import com.google.common.base.Throwables;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.time.StopWatch;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class KubernetesCloud extends Cloud {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(KubernetesCloud.class.getName());

    private String managementUrl;

    private String namespace;

    private String serverCertificate;

    private String username;

    private String clientCertificate;

    private String clientPrivateKey;

    private int idleTime;           // in minutes

    private int startupTimeout;           // in minutes

    private List<PodTemplate> templates = new ArrayList<>();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(getManagementUrl())
                .withCaCertData(getServerCertificate())
                .withNamespace(getNamespace())
                .withClientCertData(getClientCertificate())
                .withClientKeyData(getClientPrivateKey())
                .withWebsocketPingInterval(0);
        return new DefaultKubernetesClient(builder.build());
    }

    private class ProvisionCallback implements Callable<Node> {

        private final PodTemplate template;

        public ProvisionCallback(PodTemplate template) {
            this.template = template;
        }

        @Override
        public Node call() throws Exception {
            KubernetesAgent slave = null;
            RetentionStrategy retentionStrategy = null;
            try {
                if (idleTime == 0) {
                    retentionStrategy = null;
                } else {
                    retentionStrategy = new CloudRetentionStrategy(idleTime);
                }

                slave = new KubernetesAgent(KubernetesCloud.this, template, retentionStrategy);

                LOGGER.info("Adding Jenkins node: {}", slave.getNodeName());
                Jenkins.getInstance().addNode(slave);

                Pod pod = template.buildPod(slave);

                String podId = pod.getMetadata().getName();

                StopWatch stopwatch = new StopWatch();
                stopwatch.start();
                try (KubernetesClient k8sClient = connect()) {
                    pod = k8sClient.pods().inNamespace(getNamespace()).create(pod);
                    LOGGER.info("Created Pod: {}", podId);

                    // wait the pod to be running
                    while (true) {
                        if (isTimeout(stopwatch.getTime())) {
                            final String msg = String.format("Pod %s failed to start after %d minutes",
                                    podId, startupTimeout);
                            throw new TimeoutException(msg);
                        }
                        pod = k8sClient.pods().inNamespace(namespace).withName(podId).get();
                        String status = pod.getStatus().getPhase();
                        if (status.equals("Running")) {
                            break;
                        } else if (status.equals("Pending")) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ex) {
                                // do nothing
                            }
                        } else {
                            throw new IllegalStateException("Container is not running, status: " + status);
                        }
                    }
                }

                // wait the slave to be online
                while (true) {
                    if (isTimeout(stopwatch.getTime())) {
                        final String msg = String.format("Node %s failed to be online after %d minutes",
                                podId, startupTimeout);
                        throw new TimeoutException(msg);
                    }
                    if (slave.getComputer() == null) {
                        throw new IllegalStateException("Node was deleted, computer is null");
                    }
                    if (slave.getComputer().isOnline()) {
                        break;
                    }
                    Thread.sleep(1000);
                }
                return slave;
            } catch (Throwable ex) {
                LOGGER.error("Error in provisioning; slave={}, template={}", slave, template);
                if (slave != null) {
                    LOGGER.info("Removing Jenkins node: {0}", slave.getNodeName());
                    try {
                        Jenkins.getInstance().removeNode(slave);
                    } catch (IOException e) {
                        LOGGER.error("Error in cleaning up the slave node " + slave.getNodeName(), e);
                    }
                }
                throw Throwables.propagate(ex);
            }
        }
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            LOGGER.info("Excess workload after pending Spot instances: " + excessWorkload);
            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            PodTemplate template = findFirstPodTemplateBy(label);
            LOGGER.info("Template: " + template.getDisplayName());
            for (int i = 1; i <= excessWorkload; i++) {
                r.add(new NodeProvisioner.PlannedNode(template.getDisplayName(),
                        Computer.threadPoolForRemoting.submit(new ProvisionCallback(template)), 1));
            }
            return r;
        } catch (KubernetesClientException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                LOGGER.warn("Failed to connect to Kubernetes at {}: {}", getManagementUrl(), cause.getMessage());
            } else {
                LOGGER.warn("Failed to count the # of live instances on Kubernetes", cause != null ? cause : e);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to count the # of live instances on Kubernetes", e);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canProvision(Label label) {
        return findFirstPodTemplateBy(label) != null;
    }

    public PodTemplate findFirstPodTemplateBy(Label label) {
        for (PodTemplate t : getTemplates()) {
            if (label == null || label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }

    public void deletePod(String podName) {
        LOGGER.info("Terminating container instance for slave {}", podName);
        try {
            KubernetesClient client = connect();
            boolean result = client.pods().inNamespace(namespace).withName(podName).delete();
            if (result) {
                LOGGER.info("Terminated Kubernetes instance for slave {}", podName);
            } else {
                LOGGER.error("Failed to terminate pod for slave " + podName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to terminate pod for slave " + podName, e);
        }
    }

    private boolean isTimeout(long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout);
    }

    public String getManagementUrl() {
        return managementUrl;
    }

    @DataBoundSetter
    public void setManagementUrl(String managementUrl) {
        this.managementUrl = managementUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getServerCertificate() {
        return serverCertificate;
    }

    @DataBoundSetter
    public void setServerCertificate(String serverCertificate) {
        this.serverCertificate = serverCertificate;
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    public String getClientCertificate() {
        return clientCertificate;
    }

    @DataBoundSetter
    public void setClientCertificate(String clientCertificate) {
        this.clientCertificate = clientCertificate;
    }

    public String getClientPrivateKey() {
        return clientPrivateKey;
    }

    @DataBoundSetter
    public void setClientPrivateKey(String clientPrivateKey) {
        this.clientPrivateKey = clientPrivateKey;
    }

    public List<PodTemplate> getTemplates() {
        return templates;
    }

    @DataBoundSetter
    public void setTemplates(List<PodTemplate> templates) {
        this.templates = templates;
    }

    public int getIdleTime() {
        return idleTime;
    }

    @DataBoundSetter
    public void setIdleTime(int idleTime) {
        this.idleTime = idleTime;
    }

    public int getStartupTimeout() {
        return startupTimeout;
    }

    @DataBoundSetter
    public void setStartupTimeout(int startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Service(Kubernetes)";
        }
    }
}
