package com.microsoft.jenkins.containeragents.util;


import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.azure.util.AzureCredentialUtil;
import com.microsoft.jenkins.azurecommons.core.AzureClientFactory;
import com.microsoft.jenkins.azurecommons.core.credentials.TokenCredentialData;
import com.microsoft.jenkins.containeragents.ContainerPlugin;
import com.microsoft.jenkins.containeragents.Messages;
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

    public static boolean isTimeout(long startupTimeout, long elapsedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elapsedTime) >= startupTimeout);
    }

    public static boolean isHalfTimePassed(long startupTimeout, long elaspedTime) {
        return (startupTimeout > 0 && TimeUnit.MILLISECONDS.toMinutes(elaspedTime) >= startupTimeout / 2);
    }

    public static ListBoxModel listCredentialsIdItems(Item owner) {
        StandardListBoxModel listBoxModel = new StandardListBoxModel();
        listBoxModel.add("--- Select Azure Credentials ---", "");
        listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureBaseCredentials.class,
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
        if (StringUtils.isBlank(credentialsId)) {
            throw new IllegalArgumentException("Invalid credential id: " + credentialsId);
        }
        TokenCredentialData token = getToken(credentialsId);
        return getClient(token);
    }

    public static TokenCredentialData getToken(String credentialId) {
        AzureBaseCredentials credential = AzureCredentialUtil.getCredential2(credentialId);
        if (credential == null) {
            throw new NullPointerException("Can't find credential with id: " + credentialId);
        }
        return TokenCredentialData.deserialize(credential.serializeToTokenData());
    }

    public static Azure getClient(TokenCredentialData token) {
        return AzureClientFactory.getClient(token, new AzureClientFactory.Configurer() {
            @Override
            public Azure.Configurable configure(Azure.Configurable configurable) {
                return configurable
                        .withInterceptor(new ContainerPlugin.AzureTelemetryInterceptor())
                        .withUserAgent(getUserAgent());
            }
        });
    }

    private static String getUserAgent() {
        String version = null;
        String instanceId = null;
        try {
            version = AzureContainerUtils.class.getPackage().getImplementationVersion();
            instanceId = Jenkins.get().getLegacyInstanceId();
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
