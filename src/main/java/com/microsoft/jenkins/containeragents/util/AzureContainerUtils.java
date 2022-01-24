package com.microsoft.jenkins.containeragents.util;


import com.azure.core.credential.TokenCredential;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.azure.util.AzureCredentialUtil;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.Messages;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
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

    public static boolean isTimeout(long startupTimeout, long elapsedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elapsedTime) >= startupTimeout);
    }

    public static boolean isHalfTimePassed(long startupTimeout, long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout / 2);
    }

    public static ListBoxModel listResourceGroupItems(String credentialsId) throws IOException {
        ListBoxModel model = new ListBoxModel();
        model.add("--- Select Resource Group ---", "");
        if (StringUtils.isBlank(credentialsId)) {
            return model;
        }

        try {
            final AzureResourceManager azureClient = getAzureClient(credentialsId);

            for (ResourceGroup resourceGroup : azureClient.resourceGroups().list()) {
                model.add(resourceGroup.name());
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, Messages.Resource_Group_List_Failed(e));
        }
        return model;
    }

    public static AzureResourceManager getAzureClient(String credentialsId) {
        if (StringUtils.isBlank(credentialsId)) {
            throw new IllegalArgumentException("Invalid credential id: " + credentialsId);
        }
        AzureBaseCredentials credential = AzureCredentialUtil.getCredential(null, credentialsId);

        return getAzureResourceManager(credential, credential.getSubscriptionId());
    }

    private static AzureResourceManager getAzureResourceManager(
            AzureBaseCredentials azureCredentials, String subscriptionId) {
        AzureProfile profile = new AzureProfile(azureCredentials.getAzureEnvironment());
        TokenCredential tokenCredential = AzureCredentials.getTokenCredential(azureCredentials);

        return AzureResourceManager
                .configure()
                .withHttpClient(HttpClientRetriever.get())
                .authenticate(tokenCredential, profile)
                .withSubscription(subscriptionId);
    }
}
