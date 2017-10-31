package com.microsoft.jenkins.containeragents;

import com.microsoft.azure.management.Azure;
import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciService;

import com.microsoft.jenkins.containeragents.util.TokenCache;
import hudson.slaves.NodeProvisioner;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AciCloudTest extends IntegrationTest {

    @ClassRule
    public static AciRule aciRule = new AciRule();

    @Test
    public void provisionAgent() throws Exception {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<>();

        AciAgent agent = new AciAgent(aciRule.cloud, aciRule.template);
        jenkinsRule.getInstance().addNode(agent);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        AciService.createDeployment(aciRule.cloud, aciRule.template, agent, stopWatch);

        Azure azureClient = TokenCache.getInstance(aciRule.servicePrincipal).getAzureClient();
        Assert.assertNotNull(azureClient.containerGroups().getByResourceGroup(aciRule.resourceGroup, agent.getNodeName()));

        AciService.deleteAciContainerGroup(aciRule.credentialsId, aciRule.resourceGroup, agent.getNodeName(), null);
        Assert.assertNull(azureClient.containerGroups().getByResourceGroup(aciRule.resourceGroup, agent.getNodeName()));
    }
}
