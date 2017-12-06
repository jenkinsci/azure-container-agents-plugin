/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.jenkins.containeragents.remote.ISSHLaunchable;
import com.microsoft.jenkins.containeragents.remote.SSHLauncher;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesAgent extends AbstractCloudSlave implements ISSHLaunchable {

    private static final Logger LOGGER = Logger.getLogger(KubernetesAgent.class.getName());
    public static final String ROOT_FS = "/jenkins";

    private final String cloudName;

    private final String sshCredentialsId;

    private final String sshPort;

    private final String launchType;

    private String host;

    @DataBoundConstructor
    public KubernetesAgent(KubernetesCloud cloud, PodTemplate template)
            throws Descriptor.FormException, IOException {
        super(generateAgentName(template),
                template.getDescription(),
                template.getRootFs(),
                1,
                Mode.NORMAL,
                template.getLabel(),
                template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_JNLP)
                        ? new JNLPLauncher()
                        : new SSHLauncher(),
                template.getRetentionStrategy(),
                Collections.<NodeProperty<Node>>emptyList());
        cloudName = cloud.getDisplayName();
        sshCredentialsId = template.getSshCredentialsId();
        sshPort = template.getSshPort();
        launchType = template.getLaunchMethodType();
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final Computer computer = toComputer();
        if (computer == null || StringUtils.isEmpty(cloudName)) {
            return;
        }
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            return;
        }
        if (!(cloud instanceof KubernetesCloud)) {
            String msg = String.format("Cloud %s is not a KubernetesCloud", cloudName);
            LOGGER.log(Level.WARNING, msg);
            listener.fatalError(msg);
            return;
        }

        Computer.threadPoolForRemoting.execute(new Runnable() {
            @Override
            public void run() {
                ((KubernetesCloud) cloud).deletePod(name);
            }
        });
    }

    static String generateAgentName(PodTemplate template) {
        return AzureContainerUtils.generateName(template.getName(), Constants.KUBERNETES_RANDOM_NAME_LENGTH);
    }

    public String getCloudName() {
        return cloudName;
    }

    @Override
    public StandardUsernameCredentials getSshCredential() throws IllegalArgumentException {
        StandardUsernameCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernameCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(sshCredentialsId));
        if (credentials == null) {
            throw new IllegalArgumentException("Could not find credentials with id: " + sshCredentialsId);
        }

        return credentials;
    }

    @Override
    public int getSshPort() {
        return Integer.valueOf(sshPort);
    }

    @Override
    public boolean isSshLaunchType() {
        return launchType.equals(Constants.LAUNCH_METHOD_SSH);
    }

    @Override
    public String getHost() {
        return StringUtils.defaultString(host);
    }

    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public Node reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Kubernetes Agent";
        };

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
