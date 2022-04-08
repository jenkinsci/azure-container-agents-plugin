package com.microsoft.jenkins.containeragents.aci.dns;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;

public class AciDnsConfig extends AbstractDescribableImpl<AciDnsConfig> {

    private List<AciDnsServer> dnsServerNames = new ArrayList<>();

    @DataBoundConstructor
    public AciDnsConfig() {
    }

    public List<AciDnsServer> getDnsServerNames() {
        return dnsServerNames;
    }

    @DataBoundSetter
    public void setDnsServerNames(List<AciDnsServer> dnsServerNames) {
        this.dnsServerNames = dnsServerNames;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<AciDnsConfig> {

        @Override
        public String getDisplayName() {
            return "Aci DNS Config";
        }

    }

}
