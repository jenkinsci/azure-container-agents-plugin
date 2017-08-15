/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.microsoft.jenkins.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.time.StopWatch;

import javax.naming.AuthenticationException;
import java.io.File;
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

    public static BasicSSHUserPrivateKey lookupSshCredentials(final String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));
    }

    public static ContainerService getContainerService(final String azureCredentialsId,
                                                       final String resourceGroup,
                                                       final String serviceName) {
        final Azure azureClient = AzureContainerUtils.getAzureClient(azureCredentialsId);
        return azureClient.containerServices().getByResourceGroup(resourceGroup, serviceName);
    }

    public static void waitPodToOnline(final KubernetesClient client,
                                       final String podName,
                                       final String namespace,
                                       final StopWatch stopWatch,
                                       final int retryInterval,
                                       final int timeout) throws TimeoutException {
        while (true) {
            if (AzureContainerUtils.isTimeout(stopWatch.getTime(), timeout)) {
                throw new TimeoutException(Messages.Kubernetes_Pod_Start_Failed(podName, timeout));
            }

            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            String status = pod.getStatus().getPhase();
            if (status.equals("Running")) {
                break;
            } else if (status.equals("Pending")) {
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

}
