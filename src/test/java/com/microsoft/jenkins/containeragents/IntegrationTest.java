package com.microsoft.jenkins.containeragents;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import org.jvnet.hudson.test.ThreadPoolImpl;


import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IntegrationTest {

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule() {
        {
            timeout = -1;
        }

        @Override
        public void before() throws Throwable {
            super.before();
            jenkins.setSlaveAgentPort(Integer.parseInt(TestUtils.loadProperty("ACI_INBOUND_AGENT_PORT")));
            jenkins.save();
        }
    };

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

}
