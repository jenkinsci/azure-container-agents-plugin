/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents.helper;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.util.Secret;

import java.util.Collections;

public class AzureContainerServiceCredentials extends BaseStandardCredentials {

    public static class KubernetesCredential implements java.io.Serializable {

        private final Secret serverCertificate;
        private final String username;
        private final Secret clientCertificate;
        private final Secret clientKey;

        public KubernetesCredential(
                final String serverCertificate,
                final String username,
                final String clientCertificate,
                final String clientKey) {
            this.serverCertificate = Secret.fromString(serverCertificate);
            this.username = username;
            this.clientCertificate = Secret.fromString(clientCertificate);
            this.clientKey = Secret.fromString(clientKey);
        }

        public KubernetesCredential() {
            this.serverCertificate = Secret.fromString("");
            this.username = "";
            this.clientCertificate = Secret.fromString("");
            this.clientKey = Secret.fromString("");
        }

        public String getUsername() {
            return username;
        }

        public String getServerCertificate() {
            return serverCertificate.getPlainText();
        }

        public String getClientCertificate() {
            return clientCertificate.getPlainText();
        }

        public String getClientKey() {
            return clientKey.getPlainText();
        }
    }

    private final KubernetesCredential kubernetesCredentialData;

    @DataBoundConstructor
    public AzureContainerServiceCredentials(
            final CredentialsScope scope,
            final String id,
            final String description,
            final String serverCertificate,
            final String username,
            final String clientCertificate,
            final String clientKey) {
        super(scope, id, description);
        kubernetesCredentialData = new KubernetesCredential(serverCertificate, username, clientCertificate, clientKey);
    }

    public static AzureContainerServiceCredentials.KubernetesCredential getKubernetesCredential(
            final String kubernetesCredentialId) {
        AzureContainerServiceCredentials creds = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        AzureContainerServiceCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(kubernetesCredentialId));
        if (creds == null) {
            return new AzureContainerServiceCredentials.KubernetesCredential();
        }
        return creds.kubernetesCredentialData;
    }

    public String getServerCertificate() {
        return kubernetesCredentialData.serverCertificate.getEncryptedValue();
    }


    public String getUsername() {
        return kubernetesCredentialData.username;
    }

    public String getClientCertificate() {
        return kubernetesCredentialData.clientCertificate.getEncryptedValue();
    }

    public String getClientKey() {
        return kubernetesCredentialData.clientKey.getEncryptedValue();
    }

    public KubernetesCredential getKubernetesCredentialData() {
        return kubernetesCredentialData;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Microsoft Azure Container Service";
        }
    }
}
