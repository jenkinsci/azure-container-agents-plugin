package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class TestUtils {

    public static String loadProperty(final String name) {
        return loadProperty(name, "");
    }

    public static String loadProperty(final String name, final String defaultValue) {
        final String value = System.getProperty(name);
        if (StringUtils.isBlank(value)) {
            return loadEnv(name, defaultValue);
        }
        return value;
    }

    public static String loadEnv(final String name, final String defaultValue) {
        final String value = System.getenv(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    static void testProvisionAgent(String type, KubernetesRule rule, JenkinsRule jenkinsRule) throws Exception {
        KubernetesAgent agent = null;
        try {
            List<NodeProvisioner.PlannedNode> r =
                    (List<NodeProvisioner.PlannedNode>) rule.cloud.provision(new LabelAtom(type+"_TemplateTest"), 1);
            Node node = r.get(0).future.get();
            Assert.assertTrue(node instanceof KubernetesAgent);
            agent = (KubernetesAgent) node;

            //Test running job on slave
            FreeStyleProject project = jenkinsRule.createFreeStyleProject(AzureContainerUtils.generateName(type+"TestJob", 5));
            project.setAssignedLabel(new LabelAtom(type+"_TemplateTest"));
            Future<FreeStyleBuild> build = project.scheduleBuild2(0);
            jenkinsRule.assertBuildStatus(Result.SUCCESS, build.get(60, TimeUnit.SECONDS));
        } finally {
            //Test deleting pod
            if (agent != null) {
                rule.cloud.deletePod(agent.getNodeName());
                Thread.sleep(10 * 1000);
                Assert.assertNull(rule.kubernetesClient.pods().inNamespace(rule.namespace).withName(agent.getNodeName()).get());
            }
        }
    }


    private TestUtils() {

    }
}
