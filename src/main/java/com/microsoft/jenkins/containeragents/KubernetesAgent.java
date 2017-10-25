/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesAgent extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesAgent.class.getName());
    public static final String ROOT_FS = "/jenkins";

    private final String cloudName;

    @DataBoundConstructor
    public KubernetesAgent(KubernetesCloud cloud, PodTemplate template)
            throws Descriptor.FormException, IOException {
        super(generateAgentName(template),
                template.getDescription(),
                template.getRootFs(),
                1,
                Mode.NORMAL,
                template.getLabel(),
                new JNLPLauncher(),
                template.getRetentionStrategy(),
                new ArrayList<>());
        cloudName = cloud.getDisplayName();
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final Computer computer = toComputer();
        final Jenkins instance = Jenkins.getInstance();
        if (instance == null || computer == null || StringUtils.isEmpty(cloudName)) {
            return;
        }


        final Cloud cloud = instance.getCloud(cloudName);
        if (cloud == null) {
            return;
        }
        if (!(cloud instanceof KubernetesCloud)) {
            String msg = String.format("Cloud %s is not a KubernetesCloud", cloudName);
            LOGGER.log(Level.WARNING, msg);
            listener.fatalError(msg);
            return;
        }

        Computer.threadPoolForRemoting.execute(() -> ((KubernetesCloud) cloud).deletePod(name));
    }

    static String generateAgentName(PodTemplate template) {
        return AzureContainerUtils.generateName(template.getName(), Constants.KUBERNETES_RANDOM_NAME_LENGTH);
    }

    public String getCloudName() {
        return cloudName;
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
