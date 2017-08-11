package com.microsoft.azure.containeragents.aci.volumes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;


public class AzureFileVolume extends AbstractDescribableImpl<AzureFileVolume> implements Serializable {
    private static final long serialVersionUID = 8879963354L;

    private final String mountPath;
    private final String storageAccountName;
    private final String shareName;
    private final Secret storageAccountKey;

    @DataBoundConstructor
    public AzureFileVolume(final String mountPath,
                           final String storageAccountName,
                           final String shareName,
                           final Secret storageAccountKey) {
        this.mountPath = mountPath;
        this.storageAccountName = storageAccountName;
        this.shareName = shareName;
        this.storageAccountKey = storageAccountKey;
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getStorageAccountName() {
        return storageAccountName;
    }

    public String getShareName() {
        return shareName;
    }

    public String getStorageAccountKey() {
        return storageAccountKey.getEncryptedValue();
    }

    public String getStorageAccountKeyPlainText() {
        return storageAccountKey.getPlainText();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AzureFileVolume> {

        @Override
        public String getDisplayName() {
            return "Azure File Volume";
        }
    }
}
