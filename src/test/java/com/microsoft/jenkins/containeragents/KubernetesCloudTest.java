package com.microsoft.jenkins.containeragents;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.slaves.Cloud;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Created by xianyu on 7/20/2017.
 */
public class KubernetesCloudTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void testConfigRoundtrip() throws Exception {
        final KubernetesCloud cloud = new KubernetesCloud("acs-k8s");
        rule.jenkins.clouds.add(cloud);
        final HtmlForm form = rule.createWebClient().goTo("configure").getFormByName("config");
        rule.submit(form);

        final Cloud actual = rule.jenkins.clouds.get(0);
        rule.assertEqualBeans(cloud, actual, "name");
    }
}
