/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.azure.containeragents.util.Constants;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.management.compute.ContainerServiceOchestratorTypes;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.model.Item;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.time.StopWatch;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;

import javax.naming.AuthenticationException;

import static com.microsoft.azure.containeragents.KubernetesService.getContainerService;
import static com.microsoft.azure.containeragents.KubernetesService.lookupSshCredentials;


public class KubernetesCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    private String resourceGroup;

    private String serviceName;

    private String namespace;

    private String acsCredentialsId;

    private String azureCredentialsId;

    private transient volatile String masterFqdn;

    private transient AzureContainerServiceCredentials.KubernetesCredential acsCredentials;

    private int startupTimeout;           // in minutes

    private List<PodTemplate> templates = new ArrayList<>();

    private static ExecutorService threadPool;

    private transient KubernetesClient client;

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() throws AuthenticationException {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (StringUtils.isEmpty(this.masterFqdn)) {
                        ContainerService containerService = getContainerService(azureCredentialsId,
                                resourceGroup,
                                serviceName);
                        this.masterFqdn = containerService.masterFqdn();
                    }
                    client = KubernetesService.connect(this.masterFqdn, getNamespace(), getAcsCredentialsId());
                }
            }
        }
        return client;
    }

    private class ProvisionCallback implements Callable<Node> {

        private final PodTemplate template;

        ProvisionCallback(PodTemplate template) {
            this.template = template;
        }

        @Override
        public Node call() throws Exception {
            KubernetesAgent slave = null;
            final Map<String, String> properties = new HashMap<>();

            try {

                // build AI properties
                properties.put(AppInsightsConstants.AZURE_SUBSCRIPTION_ID,
                        AzureCredentials.getServicePrincipal(azureCredentialsId).getSubscriptionId());
                properties.put(Constants.AI_ACS_CREDENTIALS_TYPE,
                        lookupSshCredentials(acsCredentialsId) != null
                                ? Constants.AI_ACS_TYPE_SSH
                                : Constants.AI_ACS_TYPE_CONFIG);

                final int retryInterval = 1000;
                slave = new KubernetesAgent(KubernetesCloud.this, template);

                LOGGER.log(Level.INFO, "Adding Jenkins node: {0}", slave.getNodeName());
                Jenkins.getInstance().addNode(slave);

                //Build Secret
                Secret registrySecret = null;
                String secretName = null;
                if (!template.getPrivateRegistryCredentials().isEmpty()) {
                    secretName = name + "-" + template.getName();
                    registrySecret = template.buildSecret(namespace,
                            secretName,
                            template.getPrivateRegistryCredentials());
                }

                //Build Pod
                Pod pod = template.buildPod(slave, secretName);
                String podId = pod.getMetadata().getName();

                StopWatch stopwatch = new StopWatch();
                stopwatch.start();
                try (KubernetesClient k8sClient = connect()) {

                    properties.put(Constants.AI_ACS_MASTER_FQDN, k8sClient.getMasterUrl().toString());

                    if (registrySecret != null) {
                        k8sClient.secrets().inNamespace(namespace).createOrReplace(registrySecret);
                    }

                    pod = k8sClient.pods().inNamespace(getNamespace()).create(pod);
                    LOGGER.log(Level.INFO, "Created Pod: {0}", podId);

                    // wait the pod to be running
                    KubernetesService.waitPodToOnline(k8sClient,
                            podId,
                            namespace,
                            stopwatch,
                            retryInterval,
                            startupTimeout);
                }

                // wait the slave to be online
                while (true) {
                    if (isTimeout(stopwatch.getTime())) {
                        throw new TimeoutException(Messages.Kubernetes_Pod_Start_Failed(podId, startupTimeout));
                    }
                    if (slave.getComputer() == null) {
                        throw new IllegalStateException(Messages.Kubernetes_Pod_Deleted());
                    }
                    if (slave.getComputer().isOnline()) {
                        break;
                    }
                    Thread.sleep(retryInterval);
                }

                KubernetesPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "Provision", properties);

                return slave;
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error in provisioning; slave={0}, template={1}",
                        new Object[] {slave, template});

                properties.put("Message", ex.getMessage());
                KubernetesPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "ProvisionFailed", properties);

                if (slave != null) {
                    LOGGER.log(Level.INFO, "Removing Jenkins node: {0}", slave.getNodeName());
                    try {
                        Jenkins.getInstance().removeNode(slave);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error in cleaning up the slave node " + slave.getNodeName(), e);
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
                LOGGER.log(Level.WARNING, "Failed to connect to Kubernetes at {0}: {1}",
                        new Object[] {masterFqdn, cause.getMessage()});
            } else {
                LOGGER.log(Level.WARNING, "Failed to count the {0} of live instances on Kubernetes",
                        cause != null ? cause : e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the {0} of live instances on Kubernetes", e);
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
        LOGGER.log(Level.INFO, "Terminating container instance for slave {0}", podName);
        try (KubernetesClient client = connect()) {
            boolean result = client.pods().inNamespace(namespace).withName(podName).delete();
            if (result) {
                LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", podName);
            } else {
                LOGGER.log(Level.WARNING, "Failed to terminate pod for slave " + podName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to terminate pod for slave " + podName, e);
        }
    }

    private boolean isTimeout(long elaspedTime) {
        return KubernetesService.isTimeout(startupTimeout, elaspedTime);
    }

    public static synchronized ExecutorService getThreadPool() {
        if (KubernetesCloud.threadPool == null) {
            KubernetesCloud.threadPool = Executors.newCachedThreadPool();
        }
        return KubernetesCloud.threadPool;
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

    public int getStartupTimeout() {
        return startupTimeout;
    }

    @DataBoundSetter
    public void setStartupTimeout(int startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    public String getMasterFqdn() {
        return masterFqdn;
    }

    public KubernetesClient getClient() {
        return client;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Service(Kubernetes)";
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.add("--- Select Azure Credentials ---", "");
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillAcsCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.add("--- Select Azure Container Service Credentials ---", "");
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureContainerServiceCredentials.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillResourceGroupItems(@QueryParameter String azureCredentialsId) throws IOException {
            ListBoxModel model = new ListBoxModel();
            model.add("--- Select Resource Group ---", "");
            if (StringUtils.isBlank(azureCredentialsId)) {
                return model;
            }

            try {
                AzureCredentials.ServicePrincipal servicePrincipal
                        = AzureCredentials.getServicePrincipal(azureCredentialsId);
                final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

                List<ResourceGroup> list = azureClient.resourceGroups().list();
                for (ResourceGroup resourceGroup : list) {
                    model.add(resourceGroup.name());
                }
                return model;
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Cannot list resource group name: {}", e);
                return model;
            }
        }

        public ListBoxModel doFillServiceNameItems(@QueryParameter String azureCredentialsId,
                                                   @QueryParameter String resourceGroup) throws IOException {
            ListBoxModel model = new ListBoxModel();
            model.add("--- Select Service Name ---", "");
            if (StringUtils.isBlank(azureCredentialsId) || StringUtils.isBlank(resourceGroup)) {
                return model;
            }

            AzureCredentials.ServicePrincipal servicePrincipal
                    = AzureCredentials.getServicePrincipal(azureCredentialsId);
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
            try (KubernetesClient client = KubernetesService.connect(masterFqdn, namespace, acsCredentialsId)) {
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
