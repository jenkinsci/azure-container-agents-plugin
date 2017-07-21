package com.microsoft.azure.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

public class EmptyDirVolume extends PodVolume {

    private final String mountPath;
    private final boolean inMemory;

    @DataBoundConstructor
    public EmptyDirVolume(final String mountPath,
                          final boolean inMemory) {
        this.mountPath = mountPath;
        this.inMemory = inMemory;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewEmptyDir(getMedium())
                .build();
    }

    public boolean getImMemory() {
        return inMemory;
    }

    public String getMedium() {
        return inMemory ? "Memory" : "";
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Empty Dir Volume";
        }
    }
}
