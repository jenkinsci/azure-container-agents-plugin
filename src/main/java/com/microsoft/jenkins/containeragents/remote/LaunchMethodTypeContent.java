package com.microsoft.jenkins.containeragents.remote;

import org.kohsuke.stapler.DataBoundConstructor;

public class LaunchMethodTypeContent {
    private String sshCredentialsId;
    private String sshPort;

    @DataBoundConstructor
    public LaunchMethodTypeContent(String sshCredentialsId, String sshPort) {
        this.sshCredentialsId = sshCredentialsId;
        this.sshPort = sshPort;
    }

    public String getSshCredentialsId() {
        return sshCredentialsId;
    }

    public String getSshPort() {
        return sshPort;
    }
}
