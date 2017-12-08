package com.microsoft.jenkins.containeragents.util;


import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.azure.util.AzureMsiCredentials;
import com.microsoft.jenkins.containeragents.Messages;
import com.microsoft.jenkins.containeragents.ContainerPlugin;
import com.microsoft.rest.LogLevel;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class AzureContainerUtils {
    private static final Logger LOGGER = Logger.getLogger(AzureContainerUtils.class.getName());

    public static String generateName(String name, int randomLength) {
        final int maxNameLength = 62;
        String randString = RandomStringUtils.random(randomLength, "bcdfghjklmnpqrstvwxz0123456789");
        if (StringUtils.isEmpty(name)) {
            return String.format("%s-%s", "jenkins-agent", randString);
        }
        // no spaces
        name = name.replaceAll("[ _]", "-").toLowerCase();
        // keep it under 63 chars (62 is used to account for the '-')
        name = name.substring(0, Math.min(name.length(), maxNameLength - randString.length()));
        return String.format("%s-%s", name, randString);
    }

    private AzureContainerUtils() {

    }

    public static boolean isTimeout(long startupTimeout, long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout);
    }

    public static boolean isHalfTimePassed(long startupTimeout, long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout / 2);
    }

    public static ListBoxModel listCredentialsIdItems(Item owner) {
        StandardListBoxModel listBoxModel = new StandardListBoxModel();
        listBoxModel.add("--- Select Azure Credentials ---", "");
        listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class,
                owner,
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()));
        listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureMsiCredentials.class,
                owner,
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()));
        return listBoxModel;
    }

    public static ListBoxModel listResourceGroupItems(String credentialsId) throws IOException {
        ListBoxModel model = new ListBoxModel();
        model.add("--- Select Resource Group ---", "");
        if (StringUtils.isBlank(credentialsId)) {
            return model;
        }

        try {
            final Azure azureClient = getAzureClient(credentialsId);

            List<ResourceGroup> list = azureClient.resourceGroups().list();
            for (ResourceGroup resourceGroup : list) {
                model.add(resourceGroup.name());
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, Messages.Resource_Group_List_Failed(e));
        }
        return model;
    }

    public static Azure getAzureClient(String credentialsId) {
        AzureMsiCredentials credential = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        AzureMsiCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId));
        if (credential != null) {
            try {
                return getAzureClient(credential.getMsiPort());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            AzureCredentials.ServicePrincipal servicePrincipal = AzureCredentials.getServicePrincipal(credentialsId);
            return getAzureClient(servicePrincipal);
        }
    }

    public static Azure getAzureClient(AzureCredentials.ServicePrincipal servicePrincipal) {
        AzureEnvironment environment = getAzureEnvironment(servicePrincipal);
        AzureTokenCredentials token = new ApplicationTokenCredentials(
                servicePrincipal.getClientId(),
                servicePrincipal.getTenant(),
                servicePrincipal.getClientSecret(),
                environment);
        return Azure.configure()
                .withInterceptor(new ContainerPlugin.AzureTelemetryInterceptor())
                .withLogLevel(LogLevel.NONE)
                .withUserAgent(getUserAgent())
                .authenticate(token)
                .withSubscription(servicePrincipal.getSubscriptionId());
    }

    private static AzureEnvironment getAzureEnvironment(AzureCredentials.ServicePrincipal servicePrincipal) {
        String managementEndpoint = servicePrincipal.getManagementEndpoint();
        if (managementEndpoint.equals(AzureEnvironment.AZURE.managementEndpoint())) {
            return AzureEnvironment.AZURE;
        } else if (managementEndpoint.equals(AzureEnvironment.AZURE_CHINA.managementEndpoint())) {
            return AzureEnvironment.AZURE_CHINA;
        } else if (managementEndpoint.equals(AzureEnvironment.AZURE_GERMANY.managementEndpoint())) {
            return AzureEnvironment.AZURE_GERMANY;
        } else if (managementEndpoint.equals(AzureEnvironment.AZURE_US_GOVERNMENT.managementEndpoint())) {
            return AzureEnvironment.AZURE_US_GOVERNMENT;
        } else {
            return AzureEnvironment.AZURE;
        }
    }

    public static Azure getAzureClient(int msiPort) throws IOException {
        MsiTokenCredentials msiToken = new MsiTokenCredentials(msiPort, AzureEnvironment.AZURE);
        return Azure.configure()
                .withInterceptor(new ContainerPlugin.AzureTelemetryInterceptor())
                .withLogLevel(LogLevel.NONE)
                .withUserAgent(getUserAgent())
                .authenticate(msiToken)
                .withDefaultSubscription();
    }

    private static String getUserAgent() {
        String version = null;
        String instanceId = null;
        try {
            version = AzureContainerUtils.class.getPackage().getImplementationVersion();
            instanceId = Jenkins.getInstance().getLegacyInstanceId();
        } catch (Exception e) {
        }

        if (version == null) {
            version = "local";
        }
        if (instanceId == null) {
            instanceId = "local";
        }

        return "AzureContainerService(Kubernetes)/" + version + "/" + instanceId;
    }
}
