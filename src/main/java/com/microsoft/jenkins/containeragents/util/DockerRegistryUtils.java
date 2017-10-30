package com.microsoft.jenkins.containeragents.util;


import org.apache.commons.lang.StringUtils;

public final class DockerRegistryUtils {

    public static String formatUrlToWithProtocol(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        if (url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://")) {
            return url;
        } else {
            return "https://".concat(url);
        }
    }

    public static String formatUrlToWithoutProtocol(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        if (url.toLowerCase().startsWith("http://")) {
            return url.substring("http://".length());
        } else if (url.toLowerCase().startsWith("https://")) {
            return url.substring("https://".length());
        } else {
            return url;
        }
    }

    private DockerRegistryUtils() {

    }
}
