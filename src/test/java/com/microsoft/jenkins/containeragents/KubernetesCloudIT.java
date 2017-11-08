package com.microsoft.jenkins.containeragents;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.slaves.Cloud;
import org.junit.ClassRule;
import org.junit.Test;



public class KubernetesCloudIT extends IntegrationTest {

    @ClassRule
    public static KubernetesRule k8sRule = new KubernetesRule(KubernetesRule.K8S);

    @Test
    public void testConfigRoundtrip() throws Exception {
        final KubernetesCloud cloud = new KubernetesCloud("acs-k8s");
        jenkinsRule.jenkins.clouds.add(cloud);
        final HtmlForm form = jenkinsRule.createWebClient().goTo("configure").getFormByName("config");
        jenkinsRule.submit(form);

        final Cloud actual = jenkinsRule.jenkins.clouds.get(0);
        jenkinsRule.assertEqualBeans(cloud, actual, "name");
    }

    @Test
    public void testProvisionAgentInK8s() throws Exception {
        TestUtils.testProvisionAgent(KubernetesRule.K8S, k8sRule, jenkinsRule);
    }

}
