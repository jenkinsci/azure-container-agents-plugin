package com.microsoft.jenkins.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

public class PersistentVolumeClaim extends PodVolume {
    private String mountPath;
    private String claimName;
    private boolean readOnly;

    @DataBoundConstructor
    public PersistentVolumeClaim(final String mountPath, String claimName, boolean readOnly) {
        this.mountPath = mountPath;
        this.claimName = claimName;
        this.readOnly = readOnly;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                    .withClaimName(claimName)
                    .withReadOnly(readOnly)
                .endPersistentVolumeClaim()
                .build();
    }

    public boolean getReadOnly() {
        return readOnly;
    }

    public String getClaimName() {
        return claimName;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Persistent Volume Claim";
        }
    }
}
