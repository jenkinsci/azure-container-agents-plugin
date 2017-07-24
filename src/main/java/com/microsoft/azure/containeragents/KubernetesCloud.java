package com.microsoft.azure.containeragents;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Throwables;
import com.microsoft.azure.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.management.compute.ContainerServiceOchestratorTypes;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.model.Item;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
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

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;

import javax.naming.AuthenticationException;

import static com.microsoft.azure.containeragents.KubernetesService.getContainerService;
import static com.microsoft.azure.containeragents.KubernetesService.lookupCredentials;


public class KubernetesCloud extends Cloud {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCloud.class.getName());

    private String resourceGroup;

    private String serviceName;

    private String namespace;

    private String acsCredentialsId;

    private String azureCredentialsId;

    private volatile transient String masterFqdn;

    private transient AzureContainerServiceCredentials.KubernetesCredential acsCredentials;

    private int idleTime;           // in minutes

    private int startupTimeout;           // in minutes

    private List<PodTemplate> templates = new ArrayList<>();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() throws AuthenticationException {
//        masterFqdn = "192.168.99.100:8443";
        if (StringUtils.isEmpty(this.masterFqdn)) {
            ContainerService containerService = getContainerService(azureCredentialsId, resourceGroup, serviceName);
            this.masterFqdn = containerService.masterFqdn();
        }
        return connect(this.masterFqdn, getNamespace(), getAcsCredentialsId());
    }

    private static KubernetesClient connect(String masterFqdn, String namespace, String acsCredentialsId) throws AuthenticationException {
        try {
            if (lookupCredentials(acsCredentialsId) != null) {
                final String configContent = KubernetesService.getConfigViaSsh(masterFqdn, acsCredentialsId);
                return KubernetesClientFactory.buildWithConfigFile(configContent);
            } else {
                String managementUrl = "https://" + masterFqdn;
                return KubernetesClientFactory.buildWithKeyPair(managementUrl, namespace,
                        AzureContainerServiceCredentials.getKubernetesCredential(acsCredentialsId));
            }
        } catch (Exception e) {
            LOGGER.error("Connect failed: {}", e.getMessage());
            return null;
        }
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
                throw new RuntimeException(ex);
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
                LOGGER.warn("Failed to connect to Kubernetes at {}: {}", masterFqdn, cause.getMessage());
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
        try (KubernetesClient client = connect()) {
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

    @DataBoundSetter
    public void setAcsCredentialsId(String acsCredentialsId) {
        this.acsCredentialsId = acsCredentialsId;
        this.acsCredentials = AzureContainerServiceCredentials.getKubernetesCredential(acsCredentialsId);
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

    public String getResourceGroup() {
        return resourceGroup;
    }

    @DataBoundSetter
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getServiceName() {
        return serviceName;
    }

    @DataBoundSetter
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            return new StandardListBoxModel().withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
        }

        public ListBoxModel doFillAcsCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureContainerServiceCredentials.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class, owner, ACL.SYSTEM, Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillResourceGroupItems(@QueryParameter String azureCredentialsId) throws IOException {
            ListBoxModel model = new ListBoxModel();
            if (StringUtils.isBlank(azureCredentialsId)) {
                return model;
            }

            try {
                AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(azureCredentialsId);
                final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

                List<ResourceGroup> list = azureClient.resourceGroups().list();
                for (ResourceGroup resourceGroup : list) {
                    model.add(resourceGroup.name());
                }
                return model;
            } catch (Exception e) {
                LOGGER.info("Cannot list resource group name: {}", e);
                return model;
            }
        }

        public ListBoxModel doFillServiceNameItems(@QueryParameter String azureCredentialsId,
                                                   @QueryParameter String resourceGroup) throws IOException {
            ListBoxModel model = new ListBoxModel();
            if (StringUtils.isBlank(azureCredentialsId) || StringUtils.isBlank(resourceGroup)) {
                return model;
            }

            AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(azureCredentialsId);
            final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

            List<ContainerService> list = azureClient.containerServices().listByResourceGroup(resourceGroup);
            for (ContainerService containerService : list) {
                if (containerService.orchestratorType().equals(ContainerServiceOchestratorTypes.KUBERNETES)) {
                    model.add(containerService.name());
                }
            }

            return model;
        }

        public FormValidation doTestConnection(@QueryParameter String azureCredentialsId,
                                               @QueryParameter String resourceGroup,
                                               @QueryParameter String serviceName,
                                               @QueryParameter String namespace,
                                               @QueryParameter String acsCredentialsId) {
            if (StringUtils.isBlank(azureCredentialsId)
                    || StringUtils.isBlank(resourceGroup)
                    || StringUtils.isBlank(serviceName)
                    || StringUtils.isBlank(namespace)
                    || StringUtils.isBlank(acsCredentialsId)) {
                return FormValidation.error("Configurations cannot be empty");
            }
            ContainerService containerService = getContainerService(azureCredentialsId, resourceGroup, serviceName);
            String masterFqdn = containerService.masterFqdn();
            try (KubernetesClient client = connect(masterFqdn, namespace, acsCredentialsId)) {
                client.pods().list();
                return FormValidation.ok("Connect to %s successfully", client.getMasterUrl());
            } catch (KubernetesClientException e) {
                return FormValidation.error("Connect to %s failed", masterFqdn);
            } catch (Exception e) {
                return FormValidation.error("Connect to %s failed: %s", masterFqdn, e.getMessage());
            }
        }
    }
}
