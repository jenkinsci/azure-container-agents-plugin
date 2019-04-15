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
import com.microsoft.jenkins.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerservice.ContainerService;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.ArrayUtils;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
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


    public static KubernetesClient connect(File configFile) {
        try {
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

    public static KubernetesClient getKubernetesClient(String azureCredentialsId,
        String resourceGroup,
        String serviceName,
        String namespace,
        String acsCredentialsId) throws Exception {

        Azure azureClient = AzureContainerUtils.getAzureClient(azureCredentialsId);

        byte[] adminKubeConfigContent = azureClient.kubernetesClusters()
                    .getAdminKubeConfigContent(resourceGroup, serviceName);

        File kubeconfigFile = File.createTempFile("kube",
            ".config",
            new File(System.getProperty("java.io.tmpdir")));

        if (ArrayUtils.isEmpty(adminKubeConfigContent)) {
            ContainerService containerService
                    = KubernetesService.getContainerService(azureCredentialsId, resourceGroup, serviceName);
            String masterFqdn = containerService.masterFqdn();
            return KubernetesService.connect(masterFqdn, namespace, acsCredentialsId);
        } else {
            try (OutputStream out = new FileOutputStream(kubeconfigFile)) {
                out.write(adminKubeConfigContent);
                return KubernetesService.connect(kubeconfigFile);
            }
        }
    }

}
