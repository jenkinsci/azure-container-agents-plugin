package com.microsoft.jenkins.containeragents.aci.volumes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
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
        this.credentials = AzureStorageAccount.getStorageAccountCredential(null, credentialsId);
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

    public static AzureFileVolume get() {
        return ExtensionList.lookupSingleton(AzureFileVolume.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AzureFileVolume> {

        @Override
        public String getDisplayName() {
            return "Azure File Volume";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
            StandardListBoxModel result = new StandardListBoxModel();
            AzureFileVolume azureFileVolume = get();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(get().getCredentialsId());
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(azureFileVolume.getCredentialsId());
                }
            }
            return result
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            item,
                            AzureStorageAccount.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(
                                    AzureStorageAccount.class))
                    .includeCurrentValue(azureFileVolume.getCredentialsId());
        }
    }
}
