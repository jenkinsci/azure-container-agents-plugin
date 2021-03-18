package com.microsoft.jenkins.containeragents;


import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciService;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AciCloudIT extends IntegrationTest {

    @Rule
    public AciRule aciRule = new AciRule();

    @Test
    public void testProvisionInboundAgent() throws Exception {
        //Test provisioning node
        List<NodeProvisioner.PlannedNode> r = (List<NodeProvisioner.PlannedNode>) aciRule.cloud.provision(new LabelAtom(aciRule.label), 1);
        Node node = r.get(0).future.get();
        Assert.assertTrue(node instanceof AciAgent);
        AciAgent agent = (AciAgent) node;

        //Test running job on slave
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(AzureContainerUtils.generateName("AciTestJob", 5));
        project.setAssignedLabel(new LabelAtom(aciRule.label));
        project.getBuildersList().add(new Shell("cd /afs"));
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build.get(60, TimeUnit.SECONDS));

        //Test deleting remote agent and deployment
        AciService.deleteAciContainerGroup(aciRule.credentialsId, aciRule.resourceGroup, agent.getNodeName(), agent.getDeployName());
        Assert.assertNull(aciRule.azureClient.containerGroups().getByResourceGroup(aciRule.resourceGroup, agent.getNodeName()));

        //Test deleting Jenkins node
        agent.terminate();
        Assert.assertNull(jenkinsRule.getInstance().getNode(agent.getNodeName()));
    }
}
