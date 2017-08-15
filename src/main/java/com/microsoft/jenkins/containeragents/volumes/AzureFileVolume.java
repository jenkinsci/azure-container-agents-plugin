/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents.volumes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

public class AzureFileVolume extends PodVolume {

    private final String mountPath;
    private final String secretName;
    private final String shareName;
    private final boolean readOnly;

    @DataBoundConstructor
    public AzureFileVolume(final String mountPath,
                           final String secretName,
                           final String shareName,
                           final boolean readOnly) {
        this.mountPath = mountPath;
        this.secretName = secretName;
        this.shareName = shareName;
        this.readOnly = readOnly;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    public String getSecretName() {
        return secretName;
    }

    public String getShareName() {
        return shareName;
    }

    public boolean getReadOnly() {
        return readOnly;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewAzureFile()
                    .withShareName(getShareName())
                    .withSecretName(getSecretName())
                    .withReadOnly(getReadOnly())
                .endAzureFile()
                .build();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodVolume> {

        @Override
        public String getDisplayName() {
            return "Azure File Volume";
        }
    }
}
