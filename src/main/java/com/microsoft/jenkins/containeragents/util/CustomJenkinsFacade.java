package com.microsoft.jenkins.containeragents.util;

import jenkins.model.Jenkins;

/**
 * Facade to Jenkins instance . Encapsulates all calls to Jenkins instance so that tests can replace this facade
 * with a stub. It is inspired by Jenkins Facade of plugin-util-api-plugin.
 *
 */
public class CustomJenkinsFacade {

    public String getLegacyInstanceId() {
        return Jenkins.get().getLegacyInstanceId();
    }
}
