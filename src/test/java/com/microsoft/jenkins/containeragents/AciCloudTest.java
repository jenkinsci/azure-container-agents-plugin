package com.microsoft.jenkins.containeragents;


import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AciCloudTest extends IntegrationTest {

    @ClassRule
    public static AciRule aciRule = new AciRule();

    @Test
    public void provisionAgent() throws Exception {
        List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
        aciRule.cloud.doProvision(aciRule.template, 1, r);

        //wait to online
        r.get(0).future.get();

        FreeStyleProject project = jenkinsRule.createFreeStyleProject(AzureContainerUtils.generateName("AciTestJob", 5));
        project.setAssignedLabel(new LabelAtom("AciTemplateTest"));
        project.getBuildersList().add(new Shell("cd /afs"));
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);

        jenkinsRule.assertBuildStatus(Result.SUCCESS, build.get(60, TimeUnit.SECONDS));
    }
}
