package com.microsoft.jenkins.containeragents;

import org.junit.ClassRule;
import org.junit.Test;

public class AksIT extends IntegrationTest {
    @ClassRule
    public static KubernetesRule aksRule = new KubernetesRule(KubernetesRule.AKS);

    @Test
    public void testProvisionAgentInAks() throws Exception {
        TestUtils.testProvisionAgent(KubernetesRule.AKS, aksRule, jenkinsRule);
    }

}
