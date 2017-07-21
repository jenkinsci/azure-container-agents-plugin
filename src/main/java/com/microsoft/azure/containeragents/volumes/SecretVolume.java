package com.microsoft.azure.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by chenyl on 7/21/2017.
 */
public class SecretVolume extends PodVolume {

    private final String mountPath;
    private final String secretName;

    @DataBoundConstructor
    public SecretVolume(final String mountPath,
                        final String secretName) {
        this.mountPath = mountPath;
        this.secretName = secretName;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    public String getSecretName() {
        return secretName;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewSecret()
                    .withSecretName(getSecretName())
                .endSecret()
                .build();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {

        @Override
        public String getDisplayName() {
            return "Secret Volume";
        }
    }

}
