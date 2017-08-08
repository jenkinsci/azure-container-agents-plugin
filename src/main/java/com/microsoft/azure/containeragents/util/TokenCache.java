/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents.util;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.containeragents.KubernetesPlugin;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureCredentials;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.rest.LogLevel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

public class TokenCache {

    private static final Logger LOGGER = Logger.getLogger(TokenCache.class.getName());

    private static final Object TSAFE = new Object();

    private static TokenCache cache = null;

    private final AzureCredentials.ServicePrincipal credentials;

    public static TokenCache getInstance(final AzureCredentials.ServicePrincipal servicePrincipal) {
        synchronized (TSAFE) {
            if (cache == null) {
                cache = new TokenCache(servicePrincipal);
            } else if (cache.credentials == null
                    || !StringUtils.isEmpty(cache.credentials.getSubscriptionId())
                    || !cache.credentials.getSubscriptionId().equals(servicePrincipal.getSubscriptionId())
                    || !StringUtils.isEmpty(cache.credentials.getClientId())
                    || !cache.credentials.getClientId().equals(servicePrincipal.getClientId())
                    || !StringUtils.isEmpty(cache.credentials.getClientSecret())
                    || !cache.credentials.getClientSecret().equals(servicePrincipal.getClientSecret())
                    || !StringUtils.isEmpty(cache.credentials.getTenant())
                    || !cache.credentials.getTenant().equals(servicePrincipal.getTenant())
                    || !StringUtils.isEmpty(cache.credentials.getServiceManagementURL())
                    || !cache.credentials.getServiceManagementURL()
                        .equals(servicePrincipal.getServiceManagementURL())) {
                cache = new TokenCache(servicePrincipal);
            }
        }

        return cache;
    }

    protected TokenCache(final AzureCredentials.ServicePrincipal servicePrincipal) {
        LOGGER.log(Level.FINEST, "TokenCache: TokenCache: Instantiate new cache manager");
        this.credentials = servicePrincipal;
    }

    public static String getUserAgent() {
        String version = null;
        String instanceId = null;
        try {
            version = TokenCache.class.getPackage().getImplementationVersion();
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

    public static ApplicationTokenCredentials get(AzureCredentials.ServicePrincipal servicePrincipal) {
        //only support Azure global currently
        return new ApplicationTokenCredentials(
                servicePrincipal.getClientId(),
                servicePrincipal.getTenant(),
                servicePrincipal.getClientSecret(),
                AzureEnvironment.AZURE
//                new AzureEnvironment(new HashMap<String, String>() {
//                    {
//                        put("activeDirectoryEndpointUrl", servicePrincipal.getAuthenticationEndpoint());
//                        put("managementEndpointUrl", servicePrincipal.getServiceManagementURL());
//                        put("resourceManagerEndpointUrl", servicePrincipal.getResourceManagerEndpoint());
//                        put("activeDirectoryGraphResourceId", servicePrincipal.getGraphEndpoint());
//                    }}
//                )
        );
    }

    public Azure getAzureClient() {
        return Azure
                .configure()
                .withInterceptor(new KubernetesPlugin.AzureTelemetryInterceptor())
                .withLogLevel(LogLevel.NONE)
                .withUserAgent(getUserAgent())
                .authenticate(get(credentials))
                .withSubscription(credentials.getSubscriptionId());
    }
}
