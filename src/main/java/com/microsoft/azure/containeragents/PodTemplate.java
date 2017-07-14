package com.microsoft.azure.containeragents;

import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import io.fabric8.kubernetes.api.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;


public class PodTemplate extends AbstractDescribableImpl<PodTemplate> implements Serializable {

    private static final long serialVersionUID = 640431693814718337L;
    private static final Logger LOGGER = Logger.getLogger(PodTemplate.class.getName());

    private String name;

    private String description;

    private String image;

    private String command;

    private String args;

    private String label;

    @DataBoundConstructor
    public PodTemplate() {
    }

    public Pod buildPod(KubernetesAgent agent) {
        // Build volumes and volume mounts.
        Volume emptyDir = new VolumeBuilder()
                .withName("rootfs")
                .withNewEmptyDir()
                .endEmptyDir()
                .build();
        VolumeMount mount = new VolumeMountBuilder()
                .withName("rootfs")
                .withMountPath(KubernetesAgent.ROOT_FS)
                .build();
        String serverUrl = Jenkins.getInstance().getRootUrl();
        String nodeName = agent.getNodeName();
        String secret = agent.getComputer().getJnlpMac();
        EnvVars envs = new EnvVars("rootUrl", serverUrl, "nodeName", nodeName, "secret", secret);
        Container container = new ContainerBuilder()
                .withName(agent.getNodeName())
                .withImage(image)
                .withCommand(command.equals("") ? null : Lists.newArrayList(command))
                .withArgs(envs.expand(args).split(" "))
                .withVolumeMounts(mount)
                .build();

        Map<String, String> labels = new TreeMap<>();
        labels.put("app", "jenkins-agent");

        return new PodBuilder()
                .withNewMetadata()
                .withName(agent.getNodeName())
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withVolumes(emptyDir)
                .withContainers(container)
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public String getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "Kubernetes Pod Template";
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    public String getArgs() {
        return args;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = args;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }
    }
}
