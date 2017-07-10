package com.microsoft.azure.containeragents;

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
import java.util.*;
import java.util.logging.Logger;


public class PodTemplate extends AbstractDescribableImpl<PodTemplate> implements Serializable {

    private static final long serialVersionUID = 640431693814718337L;
    private static final Logger LOGGER = Logger.getLogger(PodTemplate.class.getName());

    private String name;

    private String description;

    private String image;

    private String command;

    private String label;

    private int idleMinutes;

    @DataBoundConstructor
    public PodTemplate() {
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

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public Pod buildPod(KubernetesAgent agent) {
        // Build volumes and volume mounts.
//        List<Volume> volumes = new ArrayList<>();
        String serverUrl = Jenkins.getInstance().getRootUrl();
        String nodeName = agent.getNodeName();
        String secret = agent.getComputer().getJnlpMac();
        EnvVars envs = new EnvVars("rootUrl", serverUrl, "nodeName", nodeName, "secret", secret);
        Container container = new ContainerBuilder()
                .withName(agent.getNodeName())
                .withImage(image)
                .withCommand(envs.expand(command))
//                .withVolumeMounts()
                .build();

        return new PodBuilder()
                .withNewMetadata()
                .withName(agent.getNodeName())
                .endMetadata()
                .withNewSpec()
//                .withVolumes(volumes)
                .withContainers(container)
                .endSpec()
                .build();
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = description;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }
    }
}
