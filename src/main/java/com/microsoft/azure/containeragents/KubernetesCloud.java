package com.microsoft.azure.containeragents;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Throwables;
import com.microsoft.azure.containeragents.helper.AzureCredentials;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel;
import hudson.model.Item;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class KubernetesCloud extends Cloud {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(KubernetesCloud.class.getName());

    private String name;

    private String managementUrl;

    private String namespace;

    private String acsCredentialsId;

    private String azureCredentialsId;

    private transient AzureCredentials.KubernetesCredential acsCredentials;

    private List<PodTemplate> templates = new ArrayList<>();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() {
        AzureCredentials.KubernetesCredential creds = AzureCredentials.getKubernetesCredential(acsCredentialsId);
        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(getManagementUrl())
                .withCaCertData(creds.getServerCertificate())
                .withNamespace(getNamespace())
                .withClientCertData(creds.getClientCertificate())
                .withClientKeyData(creds.getClientKey())
                .withWebsocketPingInterval(0);
        return new DefaultKubernetesClient(builder.build());
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
                        Computer.threadPoolForRemoting.submit(() -> {
                            KubernetesAgent slave = null;
                            RetentionStrategy retentionStrategy = null;
                            try {
                                if (template.getIdleMinutes() == 0) {
                                    retentionStrategy = null;
                                } else {
                                    retentionStrategy = new CloudRetentionStrategy(template.getIdleMinutes());
                                }

                                slave = new KubernetesAgent(template, retentionStrategy);

                                LOGGER.info("Adding Jenkins node: {}", slave.getNodeName());
                                Jenkins.getInstance().addNode(slave);

                                Pod pod = template.buildPod(slave);

                                String podId = pod.getMetadata().getName();

                                try (KubernetesClient k8sClient = connect()) {
                                    pod = k8sClient.pods().inNamespace(getNamespace()).create(pod);
                                    LOGGER.info("Created Pod: {}", podId);

                                    // wait the pod to be running
                                    while (true) {
                                        pod = k8sClient.pods().inNamespace(namespace).withName(podId).get();
                                        String status = pod.getStatus().getPhase();
                                        if (status.equals("Running")) {
                                            break;
                                        } else if (status.equals("Pending")) {
                                            Thread.sleep(1000);
                                        } else {
                                            throw new IllegalStateException("Container is not running, status: " + status);
                                        }
                                    }
                                }

                                // wait the slave to be online
                                while (true) {
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
                                    Jenkins.getInstance().removeNode(slave);
                                }
                                throw Throwables.propagate(ex);
                            }
                        }),
                        1));
            }
            return r;
        } catch (
                KubernetesClientException e)

        {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
                LOGGER.warn("Failed to connect to Kubernetes at {}: {}", getManagementUrl(), cause.getMessage());
            } else {
                LOGGER.warn("Failed to count the # of live instances on Kubernetes", cause != null ? cause : e);
            }
        } catch (
                Exception e)

        {
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

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    @DataBoundSetter
    public void setAcsCredentialsId(String acsCredentialsId) {
        this.acsCredentialsId = acsCredentialsId;
        this.acsCredentials = AzureCredentials.getKubernetesCredential(acsCredentialsId);
    }

    public String getAcsCredentialsId() {
        return acsCredentialsId;
    }

    @DataBoundSetter
    public void setAzureCredentialsId(String azureCredentialsId) {
        this.azureCredentialsId = azureCredentialsId;
    }

    public String getAzureCredentialsId() {
        return azureCredentialsId;
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

    public List<PodTemplate> getTemplates() {
        return templates;
    }

    @DataBoundSetter
    public void setTemplates(List<PodTemplate> templates) {
        this.templates = templates;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Service(Kubernetes)";
        }

        public ListBoxModel doFillAcsCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            return new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(com.microsoft.azure.util.AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
        }
    }
}
