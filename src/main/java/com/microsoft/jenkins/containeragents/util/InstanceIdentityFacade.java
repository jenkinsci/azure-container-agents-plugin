package com.microsoft.jenkins.containeragents.util;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import java.nio.charset.StandardCharsets;

/**
 * Facade to InstanceIdentity. Encapsulates all calls to tInstanceIdentity so that tests can replace this facade
 * with a stub. It is inspired by Jenkins Facade of plugin-util-api-plugin.
 *
 */
public class InstanceIdentityFacade {

    public String getInstanceId() {
        return new String(InstanceIdentity.get().getPublic().getEncoded(), StandardCharsets.UTF_8);
    }
}
