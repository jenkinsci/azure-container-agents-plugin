package com.microsoft.azure.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by chenyl on 7/21/2017.
 */
public class AzureDiskVolume extends PodVolume {

    private final String mountPath;
    private final String diskName;
    private final String diskUrl;

    @DataBoundConstructor
    public AzureDiskVolume(final String mountPath,
                           final String diskName,
                           final String diskUrl) {
        this.mountPath = mountPath;
        this.diskName = diskName;
        this.diskUrl = diskUrl;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    public String getDiskName() {
        return diskName;
    }

    public String getDiskUrl() {
        return diskUrl;
    }


    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewAzureDisk()
                    .withDiskName(getDiskName())
                    .withDiskURI(getDiskUrl())
                .endAzureDisk()
                .build();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {

        @Override
        public String getDisplayName() {
            return "Azure Disk Volume";
        }
    }
}
