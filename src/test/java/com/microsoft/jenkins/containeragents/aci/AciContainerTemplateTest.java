package com.microsoft.jenkins.containeragents.aci;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class AciContainerTemplateTest {

    @Test
    void ignoreWhitespaceInImageName() {
        AciContainerTemplate templateUnderTest = new AciContainerTemplate("name", "label", 100,
                "osType", " image ", "command" , "rootFs", null, null,
                null, null, null, "cpu", "memory");

        assertThat(templateUnderTest.getImage(), equalTo("image"));
    }
}