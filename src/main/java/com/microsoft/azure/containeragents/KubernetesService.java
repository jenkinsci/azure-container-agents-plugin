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
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import hudson.security.ACL;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import java.io.File;
import java.util.Collections;

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

}
