package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciPrivateIpAddress;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsConfig;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsServer;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static java.util.Collections.emptyList;

public class AciCloudConfigTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        AciCloud expectedAciCloud = createConfiguredAciCloud();

        AciCloud actualAciCloud = jenkins.configRoundtrip(expectedAciCloud);

        jenkins.assertEqualDataBoundBeans(expectedAciCloud, actualAciCloud);
    }

    @NotNull
    private AciCloud createConfiguredAciCloud() {
        AciContainerTemplate containerTemplate = new AciContainerTemplate("containerName", "label",
                100, "Linux", "helloworld", "command", "rootFs", emptyList(),
                emptyList(), emptyList(), emptyList(), new ContainerOnceRetentionStrategy(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        privateIpAddress.setResourceGroup("vnetResourceGroup");
        AciDnsConfig dnsConfig = new AciDnsConfig();
        dnsConfig.setDnsServers(Arrays.asList(new AciDnsServer("dnsServerAddress")));
        privateIpAddress.setDnsConfig(dnsConfig);
        containerTemplate.setPrivateIpAddress(privateIpAddress);
        AciCloud acicloud = new AciCloud("aciTest", "", "", Arrays.asList(containerTemplate));
        acicloud.setLogAnalyticsCredentialsId("");
        return acicloud;
    }
}
