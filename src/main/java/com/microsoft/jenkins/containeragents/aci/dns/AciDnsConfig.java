package com.microsoft.jenkins.containeragents.aci.dns;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AciDnsConfig extends AbstractDescribableImpl<AciDnsConfig> {

    private List<AciDnsServer> dnsServers = new ArrayList<>();

    @DataBoundConstructor
    public AciDnsConfig() {
    }

    public List<AciDnsServer> getDnsServers() {
        return dnsServers;
    }

    @DataBoundSetter
    public void setDnsServers(List<AciDnsServer> dnsServers) {
        this.dnsServers = dnsServers.stream()
                .filter(aciDnsServer -> !aciDnsServer.getAddress().isEmpty())
                .collect(Collectors.toList());
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<AciDnsConfig> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Aci DNS Config";
        }

    }

}
