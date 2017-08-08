package com.microsoft.azure.containeragents.aci;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class AciPort extends AbstractDescribableImpl<AciPort> {
    private String port;

    @DataBoundConstructor
    public AciPort(String port) {
        this.port = port;
    }

    public String getPort() {
        return port;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AciPort> {

        @Override
        public String getDisplayName() {
            return "Aci Port";
        }
    }
}
