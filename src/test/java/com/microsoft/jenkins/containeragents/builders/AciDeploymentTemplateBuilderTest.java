package com.microsoft.jenkins.containeragents.builders;


import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciPrivateIpAddress;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsConfig;
import com.microsoft.jenkins.containeragents.aci.dns.AciDnsServer;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.util.JenkinsFacade;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AciDeploymentTemplateBuilderTest {

    AciAgent agentMock = mock(AciAgent.class);
    AciDeploymentTemplateBuilder builderUnderTest;

    @Before
    public void setup(){
        SlaveComputer slaveMock = mock(SlaveComputer.class);
        when(agentMock.getComputer()).thenReturn(slaveMock);

        JenkinsFacade jenkinsFacade = mock(JenkinsFacade.class);
        when(jenkinsFacade.getLegacyInstanceId()).thenReturn("instanceId");

        builderUnderTest = new AciDeploymentTemplateBuilder(jenkinsFacade);
    }

    @Test
    public void templateWithVnet() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        template.setPrivateIpAddress(new AciPrivateIpAddress("vnet", "subnet"));

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetResourceGroupName\":\"resourceGroup\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetName\":\"vnet\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetName\":\"subnet\""));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetIds\":"));
    }

    @Test
    public void templateWithVnetAndOwnRg() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        privateIpAddress.setResourceGroup("vnetResourceGroup");
        template.setPrivateIpAddress(privateIpAddress);

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetResourceGroupName\":\"vnetResourceGroup\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetName\":\"vnet\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetName\":\"subnet\""));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetIds\":"));
    }

    @Test
    public void templateWithVnetAndOwnButEmptyRg() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        privateIpAddress.setResourceGroup("");
        template.setPrivateIpAddress(privateIpAddress);

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetResourceGroupName\":\"resourceGroup\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetName\":\"vnet\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetName\":\"subnet\""));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetIds\":"));
    }

    @Test
    public void templateWithVnetAndDnsConfig() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        AciDnsConfig dnsConfig = new AciDnsConfig();
        dnsConfig.setDnsServers(List.of(new AciDnsServer("dnsName")));
        privateIpAddress.setDnsConfig(dnsConfig);
        template.setPrivateIpAddress(privateIpAddress);

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"dnsConfig\":"));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"nameServers\":[\"dnsName\"]"));
    }

    @Test
    public void templateWithVnetAndDnsConfigWithoutDnsServer() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        AciPrivateIpAddress privateIpAddress = new AciPrivateIpAddress("vnet", "subnet");
        privateIpAddress.setDnsConfig(new AciDnsConfig());
        template.setPrivateIpAddress(privateIpAddress);

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"dnsConfig\":")));
    }


    @Test
    public void templateWithVnetWithoutDnsConfig() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        template.setPrivateIpAddress(new AciPrivateIpAddress("vnet", "subnet"));

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"dnsConfig\":")));
    }

    @Test
    public void templateWithoutVnet() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"vnetResourceGroupName\"")));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"vnetName\": \"vnet\",")));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"subnetName\": \"subnet\"")));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"subnetIds\":")));
    }
}