/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;


import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;

public class PodImagePullSecrets extends AbstractDescribableImpl<PodImagePullSecrets> implements Serializable {

    @Serial
    private static final long serialVersionUID = 694763293814718338L;
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
        @NonNull
        @Override
        public String getDisplayName() {
            return "Container Pod Image Pull Secrets";
        }
    }
}
