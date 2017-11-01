package com.microsoft.jenkins.containeragents;

import com.microsoft.azure.management.Azure;
import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciService;

import com.microsoft.jenkins.containeragents.util.TokenCache;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AciCloudTest extends IntegrationTest {

    @ClassRule
    public static AciRule aciRule = new AciRule();

    @Test
    public void provisionAgent() throws Exception {

        List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
        aciRule.cloud.doProvision(aciRule.template, 1, r);

        Node node = r.get(0).future.get();

        Assert.assertTrue(node instanceof AciAgent);


    }
}
