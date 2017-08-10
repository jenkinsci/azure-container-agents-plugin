package com.microsoft.azure.containeragents.aci.volumes;

import com.microsoft.azure.containeragents.volumes.PodVolume;
import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;


public class AzureFileVolume {
    private final String mountPath;
    private final String storageAccountName;
    private final String shareName;
    private final String storageAccountKey;

    @DataBoundConstructor
    public AzureFileVolume(final String mountPath,
                           final String storageAccountName,
                           final String shareName,
                           final String storageAccountKey) {
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
        return storageAccountKey;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {

        @Override
        public String getDisplayName() {
            return "Azure File Volume";
        }
    }
}
