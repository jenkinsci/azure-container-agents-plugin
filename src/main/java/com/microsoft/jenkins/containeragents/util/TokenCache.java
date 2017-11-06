/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents.util;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.jenkins.containeragents.ContainerPlugin;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureCredentials;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.rest.LogLevel;
import jenkins.model.Jenkins;

public class TokenCache {

    private static final Logger LOGGER = Logger.getLogger(TokenCache.class.getName());

    private static final Object TSAFE = new Object();

    private static TokenCache cache = null;

    private volatile Azure client = null;

    private final AzureCredentials.ServicePrincipal credentials;

    public static TokenCache getInstance(final AzureCredentials.ServicePrincipal servicePrincipal) throws Exception {
        synchronized (TSAFE) {
            if (cache == null || cache.credentials != servicePrincipal) {
                cache = new TokenCache(servicePrincipal);
                cache.client = cache.getAzureClient();
            }
            return cache;
        }
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

    public static ApplicationTokenCredentials get(final AzureCredentials.ServicePrincipal servicePrincipal) {
        //only support Azure global currently
        return new ApplicationTokenCredentials(
                servicePrincipal.getClientId(),
                servicePrincipal.getTenant(),
                servicePrincipal.getClientSecret(),
                new AzureEnvironment(new HashMap<String, String>() {
                    {
                        this.put("activeDirectoryEndpointUrl", servicePrincipal.getAuthenticationEndpoint());
                        this.put("activeDirectoryGraphResourceId", servicePrincipal.getGraphEndpoint());
                        this.put("managementEndpointUrl", servicePrincipal.getServiceManagementURL());
                        this.put("resourceManagerEndpointUrl", servicePrincipal.getResourceManagerEndpoint());
                        this.put("activeDirectoryResourceId", "https://management.core.windows.net/");
                    }
                })
        );
    }

    public Azure getAzureClient() throws Exception {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = Azure
                        .configure()
                        .withInterceptor(new ContainerPlugin.AzureTelemetryInterceptor())
                        .withLogLevel(LogLevel.NONE)
                        .withUserAgent(getUserAgent())
                        .authenticate(get(credentials))
                        .withSubscription(credentials.getSubscriptionId());
                }
            }
        }
        return client;
    }
}
