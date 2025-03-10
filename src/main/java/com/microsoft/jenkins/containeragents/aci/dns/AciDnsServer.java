package com.microsoft.jenkins.containeragents.aci.dns;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class AciDnsServer extends AbstractDescribableImpl<AciDnsServer> {

    private String address;

    @DataBoundConstructor
    public AciDnsServer(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AciDnsServer> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Aci DNS Server";
        }

    }
}
