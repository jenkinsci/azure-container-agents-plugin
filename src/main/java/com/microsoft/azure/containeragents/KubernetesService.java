package com.microsoft.azure.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.microsoft.azure.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.azure.containeragents.util.SSHShell;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.util.AzureCredentials;
import hudson.security.ACL;
import hudson.util.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;

public class KubernetesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesService.class.getName());


    public static String getConfigViaSsh(String masterFqdn, String acsCredentialsId) throws AuthenticationException {
        BasicSSHUserPrivateKey credentials = lookupCredentials(acsCredentialsId);

        if (credentials == null) {
            return null;
        }

        try {
            Secret passphrase = credentials.getPassphrase();
            byte[] passphraseBytes = null;
            if (passphrase != null) {
                passphraseBytes = passphrase.getPlainText().getBytes(Charset.defaultCharset());
            }
            SSHShell shell = SSHShell.open(masterFqdn, 22, credentials.getUsername(), credentials.getPrivateKey().getBytes(), passphraseBytes);
            return shell.download("config", ".kube", true);
        } catch (Exception e) {
            throw new AuthenticationException(e.getMessage());
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
