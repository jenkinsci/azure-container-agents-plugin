package com.microsoft.jenkins.containeragents.utils;

import com.microsoft.jenkins.containeragents.util.DockerRegistryUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DockerRegistryUtilsTest {
    @Test
    public void testFormatUrlToWithProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithProtocol(""));
        assertEquals("https://example.io", DockerRegistryUtils.formatUrlToWithProtocol("example.io"));
        assertEquals("https://example.io", DockerRegistryUtils.formatUrlToWithProtocol("https://example.io"));
        assertEquals("http://example.io", DockerRegistryUtils.formatUrlToWithProtocol("http://example.io"));
    }

    @Test
    public void testFormatUrlToWithoutProtocol() {
        assertEquals("", DockerRegistryUtils.formatUrlToWithoutProtocol(""));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("https://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("http://example.io"));
        assertEquals("example.io", DockerRegistryUtils.formatUrlToWithoutProtocol("example.io"));
    }
}
