package com.microsoft.jenkins.containeragents.aci;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;


public class AciContainerTemplateTest {

    @Test
    public void ignoreWhitespaceInImageName() {
        AciContainerTemplate templateUnderTest = new AciContainerTemplate("name", "label", 100,
                "osType", " image ", "command" , "rootFs", null, null,
                null, null, null, "cpu", "memory");

        assertThat(templateUnderTest.getImage(), equalTo("image"));
    }
}