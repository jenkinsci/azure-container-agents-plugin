/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.helper.AzureContainerServiceCredentials;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.File;
import java.io.IOException;

public final class KubernetesClientFactory {

    static KubernetesClient buildWithConfigFile(File configFile) throws IOException {
        System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, configFile.getPath());
        return new DefaultKubernetesClient(Config.autoConfigure());
    }

    static KubernetesClient buildWithKeyPair(final String url,
                                             final String namespace,
                                             final AzureContainerServiceCredentials
                                                     .KubernetesCredential acsCredentials) {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(url)
                .withCaCertData(acsCredentials.getServerCertificate())
                .withNamespace(namespace)
                .withClientCertData(acsCredentials.getClientCertificate())
                .withClientKeyData(acsCredentials.getClientKey())
                .withWebsocketPingInterval(0);
        return new DefaultKubernetesClient(builder.build());
    }

    private KubernetesClientFactory() {
        // hide constructor
    }
}
