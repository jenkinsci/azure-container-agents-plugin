package com.microsoft.jenkins.containeragents.util;

import jenkins.model.Jenkins;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import java.nio.charset.Charset;

/**
 * Facade to Jenkins server. Encapsulates all calls to the running Jenkins server so that tests can replace this facade
 * with a stub. It is inspired by Jenkins Facade of plugin-util-api-plugin.
 *
 */
public class CustomJenkinsFacade {

    public String getInstanceId() {
        return new String(InstanceIdentity.get().getPublic().getEncoded(), Charset.defaultCharset());
    }

    public Jenkins getJenkins() {
        return Jenkins.get();
    }
}
