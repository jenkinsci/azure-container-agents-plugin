package com.microsoft.jenkins.containeragents.utils;

import com.microsoft.jenkins.containeragents.util.DockerRegistryUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockerRegistryUtilsTest {
    @Test
    public void testFormatUrlToWithProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithProtocol(""));
        assertEquals("https://example.io", DockerRegistryUtils.formatUrlToWithProtocol("example.io"));
        assertEquals("HTTPS://example.io", DockerRegistryUtils.formatUrlToWithProtocol("HTTPS://example.io"));
        assertEquals("HTTP://example.io", DockerRegistryUtils.formatUrlToWithProtocol("HTTP://example.io"));
    }

    @Test
    public void testFormatUrlToWithoutProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithoutProtocol(""));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("HTTPS://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("HTTP://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("example.io"));
    }
}
