/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils;
import com.microsoft.jenkins.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.time.StopWatch;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KubernetesService {
    private static final Logger LOGGER = Logger.getLogger(KubernetesService.class.getName());

    private KubernetesService() {

    }

    public static File getConfigViaSsh(String masterFqdn, String acsCredentialsId) throws AuthenticationException {
        BasicSSHUserPrivateKey credentials = lookupSshCredentials(acsCredentialsId);

        if (credentials == null) {
            return null;
        }

        try {
            final int port = 22;
            try (SSHClient sshClient = new SSHClient(masterFqdn, port, credentials).connect()) {
                File configFile = File.createTempFile("kube",
                        ".config",
                        new File(System.getProperty("java.io.tmpdir")));
                sshClient.copyFrom(".kube/config", configFile);
                return configFile;
            }
        } catch (Exception e) {
            throw new AuthenticationException(e.getMessage());
        }
    }

    public static File getConfigViaBase64(String encodedConfig) throws Exception {
        byte[] data = Base64.decodeBase64(encodedConfig);

        File configFile = File.createTempFile("kube",
                ".config",
                new File(System.getProperty("java.io.tmpdir")));

        try (OutputStream stream = new FileOutputStream(configFile)) {
            stream.write(data);
            return configFile;
        }
    }

    public static KubernetesClient connect(String masterFqdn,
                                           String namespace,
                                           String acsCredentialsId) throws AuthenticationException {
        try {
            if (lookupSshCredentials(acsCredentialsId) != null) {
                File configFile = null;
                try {
                    configFile = KubernetesService.getConfigViaSsh(masterFqdn, acsCredentialsId);
                    return KubernetesClientFactory.buildWithConfigFile(configFile);
                } finally {
                    if (configFile != null) {
                        if (!configFile.delete()) {
                            LOGGER.warning("KubernetesService: connect: ConfigFile failed to delete");
                        }
                    }
                }
            } else {
                String managementUrl = "https://" + masterFqdn;
                return KubernetesClientFactory.buildWithKeyPair(managementUrl, namespace,
                        AzureContainerServiceCredentials.getKubernetesCredential(acsCredentialsId));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connect failed: {0}", e.getMessage());
            return null;
        }
    }


    public static KubernetesClient connect(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            LOGGER.log(Level.WARNING, "AKS properties is null");
            return null;
        }

        File configFile = null;
        try {
            String encodedConfig =
                    (String) properties.get("kubeConfig");
            configFile = KubernetesService.getConfigViaBase64(encodedConfig);
            return KubernetesClientFactory.buildWithConfigFile(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connect failed: {0}", e.getMessage());
            return null;
        } finally {
            if (configFile != null) {
                if (!configFile.delete()) {
                    LOGGER.warning("KubernetesService: connect: ConfigFile failed to delete");
                }
            }
        }
    }


    public static BasicSSHUserPrivateKey lookupSshCredentials(final String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId));
    }

    public static ContainerService getContainerService(final String azureCredentialsId,
                                                       final String resourceGroup,
                                                       final String serviceName) throws Exception {
        final Azure azureClient = AzureContainerUtils.getAzureClient(azureCredentialsId);
        return azureClient.containerServices().getByResourceGroup(resourceGroup, serviceName);
    }

    public static void waitPodToRunning(final KubernetesClient client,
                                        final String podName,
                                        final String namespace,
                                        final StopWatch stopWatch,
                                        final int retryInterval,
                                        final int timeout) throws TimeoutException {
        while (true) {
            if (AzureContainerUtils.isTimeout(stopWatch.getTime(), timeout)) {
                throw new TimeoutException(Messages.Kubernetes_pod_Start_Time_Exceed(podName, timeout));
            }

            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            String status = pod.getStatus().getPhase();
            if (status.equals("Running")) {
                break;
            } else if (status.equals("Pending") || status.equals("PodInitializing")) {
                if (pod.getStatus().getContainerStatuses() != null
                        && !pod.getStatus().getContainerStatuses().isEmpty()) {
                    ContainerState containerState = pod.getStatus().getContainerStatuses().get(0).getState();
                    if (containerState.getTerminated() != null) {
                        throw new IllegalStateException(Messages.Kubernetes_Container_Terminated(containerState
                                .getTerminated().getMessage()));
                    }
                    if (containerState.getWaiting() != null
                            && containerState.getWaiting().getReason().equals("ImagePullBackOff")) {
                        throw new IllegalStateException(Messages.Kubernetes_Container_Image_Pull_Backoff(containerState
                                .getWaiting().getMessage()));
                    }
                }
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException ex) {
                    // do nothing
                }
            } else {
                throw new IllegalStateException(Messages.Kubernetes_Container_Not_Running(status));
            }
        }
    }

    public static Map<String, Object> getAksProperties(String azureCredentialsId,
                                                       String resourceGroup,
                                                       String serviceName) {
        try {
            Azure azureClient = AzureContainerUtils.getAzureClient(azureCredentialsId);
            String resourceId = ResourceUtils.constructResourceId(azureClient.subscriptionId(),
                    resourceGroup,
                    Constants.AKS_NAMESPACE,
                    "accessProfiles",
                    "clusterAdmin",
                    String.format("%s/%s", Constants.AKS_RESOURCE_TYPE, serviceName));
            Object properties = azureClient.genericResources().getById(resourceId).properties();
            if (properties instanceof Map<?, ?>) {
                return (Map<String, Object>) azureClient.genericResources().getById(resourceId).properties();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static KubernetesClient getKubernetesClient(String azureCredentialsId,
                                                       String resourceGroup,
                                                       String serviceName,
                                                       String namespace,
                                                       String acsCredentialsId) throws Exception {
        Map<String, Object> properties =
                KubernetesService.getAksProperties(azureCredentialsId, resourceGroup, serviceName);

        if (properties != null) {
            return KubernetesService.connect(properties);
        } else {
            ContainerService containerService
                    = KubernetesService.getContainerService(azureCredentialsId, resourceGroup, serviceName);
            String masterFqdn = containerService.masterFqdn();
            return KubernetesService.connect(masterFqdn, namespace, acsCredentialsId);
        }
    }

}
