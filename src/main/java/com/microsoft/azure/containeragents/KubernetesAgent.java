package com.microsoft.azure.containeragents;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;

public class KubernetesAgent extends AbstractCloudSlave {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesAgent.class);
    public static final String ROOT_FS = "/jenkins";

    private final String cloudName;

    @DataBoundConstructor
    public KubernetesAgent(KubernetesCloud cloud, PodTemplate template, RetentionStrategy retentionStrategy)
            throws Descriptor.FormException, IOException {
        super(generateAgentName(template),
                template.getDescription(),
                ROOT_FS,
                1,
                Mode.NORMAL,
                template.getLabel(),
                new JNLPLauncher(),
                retentionStrategy,
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
        final String cloudName = getCloudName();
        if (computer == null || StringUtils.isEmpty(cloudName)) {
            return;
        }
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            return;
        }
        if (!(cloud instanceof KubernetesCloud)) {
            String msg = String.format("Cloud %s is not a KubernetesCloud", cloudName);
            LOGGER.error(msg);
            listener.fatalError(msg);
            return;
        }
        Computer.threadPoolForRemoting.execute(() -> ((KubernetesCloud) cloud).deletePod(name));
    }

    private static String generateAgentName(PodTemplate template) {
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = template.getName();
        if (StringUtils.isEmpty(name)) {
            return String.format("%s-%s", "jenkins-agent", randString);
        }
        // no spaces
        name = name.replaceAll("[ _]", "-").toLowerCase();
        // keep it under 63 chars (62 is used to account for the '-')
        name = name.substring(0, Math.min(name.length(), 62 - randString.length()));
        return String.format("%s-%s", name, randString);
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
