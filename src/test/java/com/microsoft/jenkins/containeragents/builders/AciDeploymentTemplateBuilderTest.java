package com.microsoft.jenkins.containeragents.builders;


import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciPrivateIpAddress;
import com.microsoft.jenkins.containeragents.util.CustomJenkinsFacade;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.util.JenkinsFacade;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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

        CustomJenkinsFacade customJenkinsFacadeMock = mock(CustomJenkinsFacade.class);
        when(customJenkinsFacadeMock.getLegacyInstanceId()).thenReturn("instanceId");

        builderUnderTest = new AciDeploymentTemplateBuilder(mock(JenkinsFacade.class), customJenkinsFacadeMock);
    }

    @Test
    public void templateWithVnet() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );
        template.setPrivateIpAddress(new AciPrivateIpAddress("vnet", "subnet"));

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"vnetName\":\"vnet\","));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"subnetName\":\"subnet\""));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), containsString("\"type\":\"Microsoft.Network/networkProfiles\""));
    }

    @Test
    public void templateWithoutVnet() throws IOException {
        AciCloud cloud = new AciCloud("testcloud", "credentialId", "resourceGroup", emptyList());

        AciContainerTemplate template = new AciContainerTemplate("containerName", "label", 100, "linux", "helloworld", "command", "rootFs", emptyList(), emptyList(), emptyList(), emptyList(), new RetentionStrategy.Always(), "cpu", "memory" );

        AciDeploymentTemplateBuilder.AciDeploymentTemplate aciDeploymentTemplate = builderUnderTest.buildDeploymentTemplate(cloud, template, agentMock);

        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"vnetName\": \"vnet\",")));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"subnetName\": \"subnet\"")));
        assertThat(aciDeploymentTemplate.deploymentTemplateAsString(), not(containsString("\"type\":\"Microsoft.Network/networkProfiles\"")));
    }
}