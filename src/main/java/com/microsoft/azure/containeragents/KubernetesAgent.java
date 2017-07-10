package com.microsoft.azure.containeragents;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.*;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KubernetesAgent extends AbstractCloudSlave {
    public KubernetesAgent(PodTemplate template, RetentionStrategy retentionStrategy) throws Descriptor.FormException, IOException {
        super(generateAgentName(template),
                template.getDescription(),
                "/",
                1,
                Mode.NORMAL,
                template.getLabel(),
                new JNLPLauncher(),
                retentionStrategy,
                new ArrayList<>());
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return null;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {

    }

    static String generateAgentName(PodTemplate template) {
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
}
