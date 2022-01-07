package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

public class JCasCTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule j, String configContent) {
        AciCloud aciCloud = (AciCloud) Jenkins.get().getCloud("Aci");
        List<AciContainerTemplate> templates = aciCloud.getTemplates();
        assertThat(templates, hasSize(1));
        AciContainerTemplate aciContainerTemplate = templates.get(0);
        assertThat(aciContainerTemplate.getName(), equalTo("aci-container"));
        assertThat(aciContainerTemplate.getPrivateIpAddress(), notNullValue());
        assertThat(aciContainerTemplate.getPrivateIpAddress().getVnet(), equalTo("vnet"));
        assertThat(aciContainerTemplate.getPrivateIpAddress().getSubnet(), equalTo("subnet"));
    }

    @Override
    protected String stringInLogExpected() {
        return "";
    }
}
