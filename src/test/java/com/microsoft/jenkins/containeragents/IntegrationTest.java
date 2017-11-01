package com.microsoft.jenkins.containeragents;

import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.net.URL;

public class IntegrationTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule() {
        {
            timeout = -1;
        }

        @Override
        public void before() throws Throwable {
            super.before();
            jenkins.setSlaveAgentPort(60000);
            jenkins.save();
        }
    };

}
