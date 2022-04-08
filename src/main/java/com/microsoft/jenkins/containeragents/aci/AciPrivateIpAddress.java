package com.microsoft.jenkins.containeragents.aci;

import com.microsoft.jenkins.containeragents.aci.dns.AciDnsConfig;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class AciPrivateIpAddress extends AbstractDescribableImpl<AciPrivateIpAddress>  {
    private String vnet;

    private String subnet;

    private String resourceGroup;

    private AciDnsConfig dnsConfig;

    @DataBoundConstructor
    public AciPrivateIpAddress(String vnet, String subnet) {
        this.vnet = vnet;
        this.subnet = subnet;
    }

    public String getVnet() {
        return vnet;
    }

    public String getSubnet() {
        return subnet;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    @DataBoundSetter
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public AciDnsConfig getDnsConfig() {
        return dnsConfig;
    }

    @DataBoundSetter
    public void setDnsConfig(AciDnsConfig dnsConfig) {
        this.dnsConfig = dnsConfig;
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<AciPrivateIpAddress> {

        @Override
        public String getDisplayName() {
            return "Aci Private IP Address";
        }
    }
}
