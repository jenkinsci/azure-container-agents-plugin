package com.microsoft.jenkins.containeragents.aci;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;

public class AciPort extends AbstractDescribableImpl<AciPort> implements Serializable {
    @Serial
    private static final long serialVersionUID = 8874521355L;
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

        @NonNull
        @Override
        public String getDisplayName() {
            return "Aci Port";
        }
    }
}
