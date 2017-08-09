package com.microsoft.azure.containeragents.util;


import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;


public final class AzureContainerUtils {

    public static String generateName(String name, int randomLength) {
        final int maxNameLength = 62;
        String randString = RandomStringUtils.random(randomLength, "bcdfghjklmnpqrstvwxz0123456789");
        if (StringUtils.isEmpty(name)) {
            return String.format("%s-%s", "jenkins-agent", randString);
        }
        // no spaces
        name = name.replaceAll("[ _]", "-").toLowerCase();
        // keep it under 63 chars (62 is used to account for the '-')
        name = name.substring(0, Math.min(name.length(), maxNameLength - randString.length()));
        return String.format("%s-%s", name, randString);
    }

    private AzureContainerUtils() {

    }
}
