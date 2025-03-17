package com.microsoft.jenkins.containeragents.utils;

import com.microsoft.jenkins.containeragents.util.DockerRegistryUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DockerRegistryUtilsTest {

    @Test
    void testFormatUrlToWithProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithProtocol(""));
        assertEquals("https://example.io", DockerRegistryUtils.formatUrlToWithProtocol("example.io"));
        assertEquals("HTTPS://example.io", DockerRegistryUtils.formatUrlToWithProtocol("HTTPS://example.io"));
        assertEquals("HTTP://example.io", DockerRegistryUtils.formatUrlToWithProtocol("HTTP://example.io"));
    }

    @Test
    void testFormatUrlToWithoutProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithoutProtocol(""));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("HTTPS://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("HTTP://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("example.io"));
    }
}
