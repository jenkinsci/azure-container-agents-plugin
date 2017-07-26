/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents;


import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class PodImagePullSecrets extends AbstractDescribableImpl<PodImagePullSecrets> implements Serializable {

    private String name;

    @DataBoundConstructor
    public PodImagePullSecrets(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<PodImagePullSecrets> {
        @Override
        public String getDisplayName() {
            return "Container Pod Image Pull Secrets";
        }
    }
}
