package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class TestUtils {

    public static String loadProperty(final String name) {
        return loadProperty(name, "");
    }

    public static String loadProperty(final String name, final String defaultValue) {
        final String value = System.getProperty(name);
        if (StringUtils.isBlank(value)) {
            return loadEnv(name, defaultValue);
        }
        return value;
    }

    public static String loadEnv(final String name, final String defaultValue) {
        final String value = System.getenv(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    private TestUtils() {

    }
}
