package com.microsoft.jenkins.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

public class HostPathVolume extends PodVolume {
    private final String mountPath;
    private final String hostPath;

    @DataBoundConstructor
    public HostPathVolume(final String mountPath,
                          final String hostPath) {
        this.mountPath = mountPath;
        this.hostPath = hostPath;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewHostPath()
                    .withPath(hostPath)
                .endHostPath()
                .build();
    }

    public String getHostPath() {
        return hostPath;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Host Path Volume";
        }
    }
}
