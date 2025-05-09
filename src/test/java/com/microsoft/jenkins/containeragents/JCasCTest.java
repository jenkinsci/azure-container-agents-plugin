package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@WithJenkins
class JCasCTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule j, String configContent) {
        AciCloud aciCloud = (AciCloud) Jenkins.get().getCloud("Aci");
        List<AciContainerTemplate> templates = aciCloud.getTemplates();
        assertThat(templates, hasSize(1));
        AciContainerTemplate aciContainerTemplate = templates.get(0);
        assertThat(aciContainerTemplate.getName(), equalTo("aci-container"));
        assertThat(aciContainerTemplate.getPrivateIpAddress(), notNullValue());
        assertThat(aciContainerTemplate.getPrivateIpAddress().getVnet(), equalTo("vnet"));
        assertThat(aciContainerTemplate.getPrivateIpAddress().getSubnet(), equalTo("subnet"));
        assertThat(aciContainerTemplate.getPrivateIpAddress().getResourceGroup(), equalTo("rg"));
        assertThat(aciContainerTemplate.getPrivateIpAddress().getDnsConfig(), notNullValue());
        assertThat(aciContainerTemplate.getPrivateIpAddress().getDnsConfig().getDnsServers(), not(empty()));
    }

    @Override
    protected String stringInLogExpected() {
        return "";
    }
}
