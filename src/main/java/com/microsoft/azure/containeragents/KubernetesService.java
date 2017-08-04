/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.microsoft.azure.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.azure.containeragents.util.Constants;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import hudson.security.ACL;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.time.StopWatch;

import javax.naming.AuthenticationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class KubernetesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesService.class.getName());

    private static KubernetesClient client = null;

    private KubernetesService() {

    }

    public static File getConfigViaSsh(String masterFqdn, String acsCredentialsId) throws AuthenticationException {
        BasicSSHUserPrivateKey credentials = lookupCredentials(acsCredentialsId);

        if (credentials == null) {
            return null;
        }

        try {
            final int port = 22;
            try (SSHClient sshClient = new SSHClient(masterFqdn, port, credentials).connect()) {
                File configFile = File.createTempFile("kube", ".config", new File(System.getProperty("java.io.tmpdir")));
                sshClient.copyFrom(".kube/config", configFile);
                return configFile;
            }
        } catch (Exception e) {
            throw new AuthenticationException(e.getMessage());
        }
    }

    public static KubernetesClient connect(String masterFqdn, String namespace, String acsCredentialsId) throws AuthenticationException {
        try {
            if (lookupCredentials(acsCredentialsId) != null) {
                File configFile = null;
                try {
                    configFile = KubernetesService.getConfigViaSsh(masterFqdn, acsCredentialsId);
                    return KubernetesClientFactory.buildWithConfigFile(configFile);
                } finally {
                    if (configFile != null) {
                        if (!configFile.delete()) {
                            LOGGER.error("KubernetesService: connect: ConfigFile failed to delete");
                        }
                    }
                }
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

    public static BasicSSHUserPrivateKey lookupCredentials(final String credentialsId) {
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
        AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(azureCredentialsId);
        final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
        return azureClient.containerServices().getByResourceGroup(resourceGroup, serviceName);
    }

    public static Pod createAciConnectorPod(final AzureCredentials.ServicePrincipal servicePrincipal,
                                            final String aciResourceGroup) {

        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVar("AZURE_CLIENT_ID", servicePrincipal.getClientId(), null));
        envVars.add(new EnvVar("AZURE_CLIENT_KEY", servicePrincipal.getClientSecret(), null));
        envVars.add(new EnvVar("AZURE_TENANT_ID", servicePrincipal.getTenant(), null));
        envVars.add(new EnvVar("AZURE_SUBSCRIPTION_ID", servicePrincipal.getSubscriptionId(), null));
        envVars.add(new EnvVar("ACI_RESOURCE_GROUP", aciResourceGroup, null));

        Container aciContainer = new ContainerBuilder()
                .withName(Constants.ACI_CONTAINER_NAME)
                .withImage(Constants.ACI_CONTAINER_IMAGE)
                .withImagePullPolicy("Always")
                .withEnv(envVars)
                .build();

        return new PodBuilder()
                .withNewMetadata()
                    .withName(Constants.ACI_POD_NAME)
                    .withNamespace(Constants.ACI_POD_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                    .withContainers(aciContainer)
                .endSpec()
                .build();
    }

    public static void createAciAndWaitToOnline(final KubernetesClient client,
                                                final Pod aciConnectorPod,
                                                final String namespace,
                                                final StopWatch stopWatch,
                                                final int retryInterval,
                                                final int timeout) throws TimeoutException {
        client.pods().inNamespace(namespace).create(aciConnectorPod);
        waitPodToOnline(client, Constants.ACI_POD_NAME, namespace, stopWatch, retryInterval, timeout);
    }

    public static void waitPodToOnline(final KubernetesClient client,
                                       final String podName,
                                       final String namespace,
                                       final StopWatch stopWatch,
                                       final int retryInterval,
                                       final int timeout) throws TimeoutException {
        while (true) {
            if (isTimeout(stopWatch.getTime(), timeout)) {
                final String msg = String.format("Pod %s failed to start after %d minutes",
                        podName, timeout);
                throw new TimeoutException(msg);
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
                throw new IllegalStateException("Container is not running, status: " + status);
            }
        }
    }

    public static boolean isTimeout(long startupTimeout, long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout);
    }

}
