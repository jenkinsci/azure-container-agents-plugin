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
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;

import javax.naming.AuthenticationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

public class KubernetesService {
    private static final Logger LOGGER = Logger.getLogger(KubernetesService.class.getName());


    private static String getConfigViaSsh(String masterFqdn, String acsCredentialsId) throws AuthenticationException {
        BasicSSHUserPrivateKey credentials = lookupCredentials(acsCredentialsId);

        if (credentials == null) {
            return null;
        }

        try {
            SSHShell shell = SSHShell.open(masterFqdn, 22, credentials.getUsername(), credentials.getPrivateKey().getBytes());
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

    public static KubernetesClient getKubernetesClient(final String masterFqdn, final String acsCredentialsId) throws IOException, AuthenticationException {
        final String configContent = getConfigViaSsh(masterFqdn, acsCredentialsId);

        File tempKubeConfigFile = File.createTempFile("kube", ".config", new File(System.getProperty("java.io.tmpdir")));
        tempKubeConfigFile.deleteOnExit();

        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tempKubeConfigFile));
        bufferedWriter.write(configContent);
        bufferedWriter.close();

        System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, tempKubeConfigFile.getPath());
        return new DefaultKubernetesClient(Config.autoConfigure());
    }

    public static KubernetesClient getKubernetesClient(final String url,
                                                       final String namespace,
                                                       final AzureContainerServiceCredentials.KubernetesCredential acsCredentials) {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(url)
                .withCaCertData(acsCredentials.getServerCertificate())
                .withNamespace(namespace)
                .withClientCertData(acsCredentials.getClientCertificate())
                .withClientKeyData(acsCredentials.getClientKey())
                .withWebsocketPingInterval(0);
        return new DefaultKubernetesClient(builder.build());
    }

    public static ContainerService getContainerService(final String azureCredentialsId,
                                                       final String resourceGroup,
                                                       final String serviceName) {
        AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(azureCredentialsId);
        final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
        return azureClient.containerServices().getByResourceGroup(resourceGroup, serviceName);
    }

}
