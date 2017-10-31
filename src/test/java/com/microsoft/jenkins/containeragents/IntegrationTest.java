package com.microsoft.jenkins.containeragents;

import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

public class IntegrationTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule() {
        {
            timeout = -1;
        }
    };

}
