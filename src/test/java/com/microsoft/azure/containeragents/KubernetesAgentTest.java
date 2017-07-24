package com.microsoft.azure.containeragents;

import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.StringStartsWith;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.io.IOException;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.*;

/**
 * Created by xianyu on 7/20/2017.
 */
public class KubernetesAgentTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    private KubernetesAgent agent;

    @Before
    public void setup() throws IOException, Descriptor.FormException {
        PodTemplate podTemplate = new PodTemplate();
        KubernetesCloud cloud = Mockito.mock(KubernetesCloud.class);
        agent = new KubernetesAgent(cloud, podTemplate, null);
        rule.jenkins.addNode(agent);
    }

    @Test
    public void testGenerateAgentName() {
        PodTemplate podTemplate = new PodTemplate();
        assertThat(KubernetesAgent.generateAgentName(podTemplate), startsWith("jenkins-agent"));
        podTemplate.setName("testname");
        assertThat(KubernetesAgent.generateAgentName(podTemplate), startsWith("testname"));
    }

    @Test
    public void testTerminate() throws IOException, InterruptedException {
        Assert.assertNotEquals(0, rule.jenkins.getNodes().size());
        agent.terminate();
        Assert.assertEquals(0, rule.jenkins.getNodes().size());
    }
}
