package com.microsoft.jenkins.containeragents;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciPrivateIpAddress;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsConfig;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsServer;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static java.util.Collections.emptyList;

@WithJenkins
class AciCloudConfigTest {

    @Test
    void configRoundTrip(JenkinsRule jenkins) throws Exception {
        String cloudName = "aciTest";
        AciCloud expectedAciCloud = createConfiguredAciCloud(cloudName);

        jenkins.jenkins.clouds.add(expectedAciCloud);
        jenkins.jenkins.save();
        JenkinsRule.WebClient testClient = jenkins.createWebClient();
        HtmlPage cloudPage = testClient.goTo("configureClouds/");
        HtmlForm configForm = cloudPage.getFormByName("config");
        jenkins.submit(configForm);

        AciCloud actualAciCloud = (AciCloud) jenkins.jenkins.getCloud(cloudName);
        jenkins.assertEqualDataBoundBeans(expectedAciCloud, actualAciCloud);
    }

    @NonNull
    private AciCloud createConfiguredAciCloud(String cloudName) {
        AciContainerTemplate containerTemplate = new AciContainerTemplate("containerName", "label",
                100, "Linux", "helloworld", "command", "rootFs", emptyList(),
                emptyList(), emptyList(), emptyList(), new ContainerOnceRetentionStrategy(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        privateIpAddress.setResourceGroup("vnetResourceGroup");
        AciDnsConfig dnsConfig = new AciDnsConfig();
        dnsConfig.setDnsServers(List.of(new AciDnsServer("dnsServerAddress")));
        privateIpAddress.setDnsConfig(dnsConfig);
        containerTemplate.setPrivateIpAddress(privateIpAddress);
        AciCloud acicloud = new AciCloud(cloudName, "", "", List.of(containerTemplate));
        acicloud.setLogAnalyticsCredentialsId("");
        return acicloud;
    }
}
