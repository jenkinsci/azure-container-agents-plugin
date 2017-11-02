package com.microsoft.jenkins.containeragents;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class KubernetesCloudTest extends IntegrationTest{

    @Rule
    public KubernetesRule k8sRule = new KubernetesRule(KubernetesRule.K8S);

    @Rule
    public KubernetesRule aksRule = new KubernetesRule(KubernetesRule.AKS);

    @Test
    public void testConfigRoundtrip() throws Exception {
        final KubernetesCloud cloud = new KubernetesCloud("acs-k8s");
        jenkinsRule.jenkins.clouds.add(cloud);
        final HtmlForm form = jenkinsRule.createWebClient().goTo("configure").getFormByName("config");
        jenkinsRule.submit(form);

        final Cloud actual = jenkinsRule.jenkins.clouds.get(0);
        jenkinsRule.assertEqualBeans(cloud, actual, "name");
    }

    @Ignore
    @Test
    public void testProvisionAgentInK8s() throws Exception {
        List<NodeProvisioner.PlannedNode> r =
            (List<NodeProvisioner.PlannedNode>) k8sRule.cloud.provision(new LabelAtom("K8S_TemplateTest"), 1);
        Node node = r.get(0).future.get();
        Assert.assertTrue(node instanceof KubernetesAgent);
        KubernetesAgent agent = (KubernetesAgent) node;

        //Test running job on slave
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(AzureContainerUtils.generateName("K8sTestJob", 5));
        project.setAssignedLabel(new LabelAtom("K8S_TemplateTest"));
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build.get(60, TimeUnit.SECONDS));

        //Test deleting pod
        k8sRule.cloud.deletePod(agent.getNodeName());
        Assert.assertNull(k8sRule.kubernetesClient.pods().inNamespace(k8sRule.namespace).withName(agent.getNodeName()).get());


    }

    @Ignore
    @Test
    public void testProvisionAgentInAks() throws Exception {

    }
}
