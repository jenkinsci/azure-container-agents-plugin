package com.microsoft.jenkins.containeragents.aci;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class AciPrivateIpAddress extends AbstractDescribableImpl<AciPrivateIpAddress> implements Serializable {

    private static final long serialVersionUID = -2085331194292057035L;

    private String vnet;

    private String subnet;

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

    @Extension
    public static class DescriptorImpl extends Descriptor<AciPrivateIpAddress> {

        @Override
        public String getDisplayName() {
            return "Aci Private IP Address";
        }
    }
}
