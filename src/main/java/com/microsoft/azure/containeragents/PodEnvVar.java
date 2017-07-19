package com.microsoft.azure.containeragents;


import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class PodEnvVar extends AbstractDescribableImpl<PodEnvVar> implements Serializable {

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
        @Override
        public String getDisplayName() {
            return "Container Environment Variable";
        }
    }
}
