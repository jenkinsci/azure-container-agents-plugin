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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;

import static com.microsoft.azure.containeragents.KubernetesService.getContainerService;
import static com.microsoft.azure.containeragents.KubernetesService.getKubernetesClient;
import static com.microsoft.azure.containeragents.KubernetesService.lookupCredentials;


public class KubernetesCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    private String name;

    private String resourceGroup;

    private String serviceName;

    private String namespace;

    private String acsCredentialsId;

    private String azureCredentialsId;

    private transient AzureContainerServiceCredentials.KubernetesCredential acsCredentials;

    private List<PodTemplate> templates = new ArrayList<>();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() {

        ContainerService containerService = getContainerService(getAzureCredentialsId(), getResourceGroup(), getServiceName());

        String url = "https://" + containerService.masterFqdn();
        try {
            if (lookupCredentials(getAcsCredentialsId()) != null) {
                return getKubernetesClient(containerService.masterFqdn(), getAcsCredentialsId());
            } else {
                return getKubernetesClient(url, getNamespace(), AzureContainerServiceCredentials.getKubernetesCredential(getAcsCredentialsId()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connect failed: {0}", e.getMessage());
            return null;
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

                                LOGGER.log(Level.INFO,"Adding Jenkins node: {0}", slave.getNodeName());
                                Jenkins.getInstance().addNode(slave);

                                Pod pod = template.buildPod(slave);

                                String podId = pod.getMetadata().getName();

                                try (KubernetesClient k8sClient = connect()) {
                                    pod = k8sClient.pods().inNamespace(getNamespace()).create(pod);
                                    LOGGER.log(Level.INFO, "Created Pod: {}", podId);

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
                                LOGGER.log(Level.WARNING, "Error in provisioning; slave={0}, template={1}", new Object[]{slave, template});
                                if (slave != null) {
                                    LOGGER.log(Level.INFO, "Removing Jenkins node: {0}", slave.getNodeName());
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
                LOGGER.log(Level.WARNING, "Failed to connect to Kubernetes at {}: {}", new Object[]{getManagementUrl(), cause.getMessage()});
            } else {
                LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", cause != null ? cause : e);
            }
        } catch (
                Exception e)

        {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
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

    public String getManagementUrl() {
        return "https://chenylmgmt.southeastasia.cloudapp.azure.com";
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
                LOGGER.log(Level.WARNING, "Cannot list resource group name: {0}", e);
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

            AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(azureCredentialsId);
            String url = "Unknown Server";
            try {
                final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
                ContainerService containerService = azureClient.containerServices().getByResourceGroup(resourceGroup, serviceName);
                url = "https://" + containerService.masterFqdn();

                KubernetesClient client;
                if (lookupCredentials(acsCredentialsId) != null) {
                    client = getKubernetesClient(containerService.masterFqdn(), acsCredentialsId);
                } else {
                    client = getKubernetesClient(url, namespace, AzureContainerServiceCredentials.getKubernetesCredential(acsCredentialsId));
                }

                client.pods().list();

                return FormValidation.ok("Connect to %s successfully", url);
            } catch (KubernetesClientException e) {
                return FormValidation.error("Connect to %s failed", url);
            } catch (Exception e) {
                return FormValidation.error("Connect to %s failed: %s", url, e.getMessage());
            }
        }
    }
}
