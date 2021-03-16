package com.microsoft.jenkins.containeragents.remote;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

public interface ISSHLaunchable {

    StandardUsernameCredentials getSshCredential();

    int getSshPort();

    boolean isSshLaunchType();

    String getHost();
}
