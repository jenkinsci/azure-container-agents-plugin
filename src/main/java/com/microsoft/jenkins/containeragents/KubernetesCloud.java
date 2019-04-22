/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerservice.ContainerService;
import com.microsoft.azure.management.containerservice.ContainerServiceOrchestratorTypes;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants;
import com.microsoft.jenkins.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.jenkins.containeragents.strategy.ProvisionRetryStrategy;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
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

    private transient volatile KubernetesClient client;

    private transient ProvisionRetryStrategy provisionRetryStrategy = new ProvisionRetryStrategy();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    private KubernetesClient connect() throws Exception {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = KubernetesService.getKubernetesClient(azureCredentialsId,
                            resourceGroup,
                            getServiceNameWithoutOrchestra(serviceName),
                            namespace,
                            acsCredentialsId);
                }
            }
        }
        return client;
    }

    private class ProvisionCallback implements Callable<Node> {

        private final PodTemplate template;

        private static final int RETRY_INTERVAL = 1000;

        ProvisionCallback(PodTemplate template) {
            this.template = template;
        }

        @Override
        public Node call() throws Exception {
            KubernetesAgent slave = null;
            final Map<String, String> properties = new HashMap<>();

            try {
                slave = new KubernetesAgent(KubernetesCloud.this, template);

                LOGGER.log(Level.INFO, "Adding Jenkins node: {0}", slave.getNodeName());
                Jenkins.getInstance().addNode(slave);

                // build AI properties
                properties.put(AppInsightsConstants.AZURE_SUBSCRIPTION_ID,
                        AzureCredentials.getServicePrincipal(azureCredentialsId).getSubscriptionId());
                properties.put(Constants.AI_ACS_CREDENTIALS_TYPE,
                        KubernetesService.lookupSshCredentials(acsCredentialsId) != null
                                ? Constants.AI_ACS_TYPE_SSH
                                : Constants.AI_ACS_TYPE_CONFIG);
                properties.put(Constants.AI_CONTAINER_NAME, slave.getNodeName());

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

                    k8sClient.pods().inNamespace(getNamespace()).create(pod);
                    LOGGER.log(Level.INFO, "KubernetesCloud: Pending Pod: {0}", podId);
                    // wait the pod to be running
                    KubernetesService.waitPodToRunning(k8sClient,
                            podId,
                            namespace,
                            stopwatch,
                            RETRY_INTERVAL,
                            startupTimeout);
                    LOGGER.log(Level.INFO, "KubernetesCloud: Pod {0} is running successfully,"
                            + "waiting to be online", podId);

                    if (template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_JNLP)) {
                        //wait JNLP to online
                        waitToOnline(slave, podId, stopwatch);
                    } else {
                        addHost(slave, client, podId);
                        slave.toComputer().connect(false).get();
                    }
                }

                provisionRetryStrategy.success(template.getName());
                ContainerPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "Provision", properties);

                return slave;
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error in provisioning; slave={0}, template={1}: {2}",
                        new Object[] {slave, template, ex});

                properties.put("Message", ex.getMessage());
                ContainerPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "ProvisionFailed", properties);

                if (slave != null) {
                    LOGGER.log(Level.INFO, "Removing Jenkins node: {0}", slave.getNodeName());
                    try {
                        slave.terminate();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error in cleaning up the slave node " + slave.getNodeName(), e);
                    }
                }
                provisionRetryStrategy.failure(template.getName());
                throw ex;
            }
        }

        private void waitToOnline(KubernetesAgent slave, String podId, StopWatch stopwatch) throws Exception {
            while (true) {
                if (isTimeout(stopwatch.getTime())) {
                    throw new TimeoutException(Messages.Kubernetes_pod_Start_Time_Exceed(podId, startupTimeout));
                }
                Pod podTemp = client.pods().inNamespace(namespace).withName(podId).get();
                if (!podTemp.getStatus().getPhase().equals("Running")) {
                    throw new IllegalStateException(Messages.Kubernetes_Pod_Start_Failed(podId,
                            podTemp.getStatus().getPhase()));
                }
                if (slave.getComputer() == null) {
                    throw new IllegalStateException(Messages.Kubernetes_Pod_Deleted());
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                Thread.sleep(RETRY_INTERVAL);
            }
        }

        private void addHost(KubernetesAgent slave,
                             KubernetesClient kubernetesClient,
                             String podId) throws IOException {
            slave.setHost(kubernetesClient.pods().inNamespace(namespace).withName(podId).get().getStatus().getPodIP());
            slave.save();
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
                LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes",
                        cause != null ? cause : e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canProvision(Label label) {
        final PodTemplate template = findFirstPodTemplateBy(label);
        return template != null && provisionRetryStrategy.isEnabled(template.getName());
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
        final Map<String, String> properties = new HashMap<>();

        try (KubernetesClient client = connect()) {
            properties.put(Constants.AI_CONTAINER_NAME, podName);

            boolean result = client.pods().inNamespace(namespace).withName(podName).delete();

            ContainerPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "Deleted", properties);

            if (result) {
                LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", podName);
            } else {
                LOGGER.log(Level.WARNING, "Failed to terminate pod for slave " + podName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to terminate pod for slave " + podName, e);

            properties.put("Message", e.getMessage());
            ContainerPlugin.sendEvent(Constants.AI_CONTAINER_AGENT, "DeletedFailed", properties);
        }
    }

    private boolean isTimeout(long elapsedTime) {
        return AzureContainerUtils.isTimeout(startupTimeout, elapsedTime);
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

    private Object readResolve() {
        this.provisionRetryStrategy = new ProvisionRetryStrategy();
        return this;
    }

    public static String getServiceNameWithoutOrchestra(String serviceName) {
        if (StringUtils.isBlank(serviceName)) {
            return serviceName;
        }
        return StringUtils.substringBeforeLast(serviceName, "|").trim();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Service / Kubernetes Service";
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            return AzureContainerUtils.listCredentialsIdItems(owner);
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
            return AzureContainerUtils.listResourceGroupItems(azureCredentialsId);
        }

        public ListBoxModel doFillServiceNameItems(@QueryParameter String azureCredentialsId,
                                                   @QueryParameter String resourceGroup) throws IOException {
            ListBoxModel model = new ListBoxModel();
            model.add("--- Select Service Name ---", "");
            if (StringUtils.isBlank(azureCredentialsId) || StringUtils.isBlank(resourceGroup)) {
                return model;
            }
            try {
                final Azure azureClient = AzureContainerUtils.getAzureClient(azureCredentialsId);

                //Add ACS(kubernetes)
                List<ContainerService> list = azureClient.containerServices().listByResourceGroup(resourceGroup);
                for (ContainerService containerService : list) {
                    if (containerService.orchestratorType().equals(ContainerServiceOrchestratorTypes.KUBERNETES)) {
                        model.add(containerService.name());
                    }
                }

                //Add ACS(AKS)
                List<GenericResource> genericResourceList
                        = azureClient.genericResources().listByResourceGroup(resourceGroup);
                for (GenericResource genericResource: genericResourceList) {
                    if (genericResource.resourceProviderNamespace().equals(Constants.AKS_NAMESPACE)
                            && genericResource.resourceType().equals(Constants.AKS_RESOURCE_TYPE)) {
                        model.add(genericResource.name() + " | AKS");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, Messages.Container_Service_List_Failed(e));
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
                    || StringUtils.isBlank(namespace)) {
                return FormValidation.error("Configurations cannot be empty");
            }
            String masterFqdn = null;
            try (KubernetesClient client = KubernetesService.getKubernetesClient(azureCredentialsId,
                        resourceGroup,
                        getServiceNameWithoutOrchestra(serviceName),
                        namespace,
                        acsCredentialsId)) {
                masterFqdn = client.getMasterUrl().toString();
                try {
                    client.pods().list();
                    return FormValidation.ok("Connect to %s successfully", client.getMasterUrl());
                } catch (KubernetesClientException e) {
                    return FormValidation.error("Connect to %s failed", masterFqdn);
                }
            } catch (Exception e) {
                return FormValidation.error("Connect to %s failed: %s", masterFqdn, e.getMessage());
            }
        }
    }
}
