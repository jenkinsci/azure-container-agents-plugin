package com.microsoft.jenkins.containeragents.aci.volumes;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Collections;


public class AzureFileVolume extends AbstractDescribableImpl<AzureFileVolume> implements Serializable {
    private static final long serialVersionUID = 8879963354L;

    private final String mountPath;
    private final String shareName;
    private final String credentialsId;
    private final AzureStorageAccount.StorageAccountCredential credentials;

    @DataBoundConstructor
    public AzureFileVolume(final String mountPath,
                           final String shareName,
                           final String credentialsId) {
        this.mountPath = mountPath;
        this.shareName = shareName;
        this.credentialsId = credentialsId;
        this.credentials = AzureStorageAccount.getStorageAccountCredential(credentialsId);
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getShareName() {
        return shareName;
    }

    public String getStorageAccountName() {
        return credentials.getStorageAccountName();
    }

    public String getStorageAccountKey() {
        return credentials.getStorageAccountKey();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AzureFileVolume> {

        @Override
        public String getDisplayName() {
            return "Azure File Volume";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel model = new StandardListBoxModel();
            model.withAll(CredentialsProvider.lookupCredentials(AzureStorageAccount.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return model;
        }
    }
}
