package com.microsoft.jenkins.containeragents;

import org.apache.commons.lang.StringUtils;

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
