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

public class PodEnvVar extends AbstractDescribableImpl<PodEnvVar> implements Serializable {

    @Serial
    private static final long serialVersionUID = 694763293814718337L;

    private String key;
    private String value;

    @DataBoundConstructor
    public PodEnvVar(String key,
                     String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodEnvVar> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Container Environment Variable";
        }
    }
}
