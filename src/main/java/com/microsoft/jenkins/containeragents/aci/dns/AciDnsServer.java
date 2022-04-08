package com.microsoft.jenkins.containeragents.aci.dns;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class AciDnsServer extends AbstractDescribableImpl<AciDnsServer> {

    private String dnsServer;

    @DataBoundConstructor
    public AciDnsServer(String dnsServer) {
        this.dnsServer = dnsServer;
    }

    public String getDnsServer() {
        return dnsServer;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AciDnsServer> {

        @Override
        public String getDisplayName() {
            return "Aci DNS Server";
        }

    }
}
