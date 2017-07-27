/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.microsoft.azure.containeragents.strategy.KubernetesIdleRetentionStrategy;
import com.microsoft.azure.containeragents.strategy.KubernetesOnceRetentionStrategy;
import com.microsoft.azure.containeragents.util.DockerConfigBuilder;
import com.microsoft.azure.containeragents.volumes.PodVolume;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.slaves.RetentionStrategy;
import hudson.util.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;


public class PodTemplate extends AbstractDescribableImpl<PodTemplate> implements Serializable {

    private static final long serialVersionUID = 640431693814718337L;

    private String name;

    private String description;

    private String image;

    private String command;

    private String args;

    private String label;

    private String rootFs;

    private RetentionStrategy<?> retentionStrategy;

    private boolean privileged;

    private String requestCpu;

    private String limitCpu;

    private String requestMemory;

    private String limitMemory;

    private List<PodEnvVar> envVars = new ArrayList<>();

    private List<PodVolume> volumes = new ArrayList<>();

    private List<PodImagePullSecrets> imagePullSecrets = new ArrayList<>();

    private List<DockerRegistryEndpoint> privateRegistryCredentials = new ArrayList<>();

    public static final String LABEL_KEY = "app";

    public static final String LABEL_VALUE = "jenkins-agent";


    @DataBoundConstructor
    public PodTemplate() {
    }

    public Pod buildPod(KubernetesAgent agent, String additionalSecret) {
        // Build volumes and volume mounts.
        List<Volume> tempVolumes = new ArrayList<>();
        Volume emptyDir = new VolumeBuilder()
                .withName("rootfs")
                .withNewEmptyDir()
                .endEmptyDir()
                .build();

        tempVolumes.add(emptyDir);

        List<VolumeMount> volumeMounts = new ArrayList<>();

        VolumeMount mount = new VolumeMountBuilder()
                .withName("rootfs")
                .withMountPath(rootFs)
                .build();

        volumeMounts.add(mount);

        for (int index = 0; index < volumes.size(); index++) {
            PodVolume podVolume = volumes.get(index);
            String volumeName = "volume-" + index;
            tempVolumes.add(podVolume.buildVolume(volumeName));

            volumeMounts.add(new VolumeMountBuilder()
                                .withName(volumeName)
                                .withMountPath(podVolume.getMountPath())
                                .build());
        }

        List<EnvVar> envVars = new ArrayList<>();
        for (PodEnvVar envVar : getEnvVars()) {
            envVars.add(new EnvVar(envVar.getKey(), envVar.getValue(), null));
        }

        List<LocalObjectReference> imagePullSecrets = new ArrayList<>();
        for (PodImagePullSecrets secret : getImagePullSecrets()) {
            imagePullSecrets.add(new LocalObjectReference(secret.getName()));
        }
        if (additionalSecret != null) {
            imagePullSecrets.add(new LocalObjectReference(additionalSecret));
        }

        String serverUrl = Jenkins.getInstance().getRootUrl();
        String nodeName = agent.getNodeName();
        String secret = agent.getComputer().getJnlpMac();
        EnvVars arguments = new EnvVars("rootUrl", serverUrl, "nodeName", nodeName, "secret", secret);
        Container container = new ContainerBuilder()
                .withName(agent.getNodeName())
                .withImage(image)
                .withCommand(command.equals("") ? null : Lists.newArrayList(command))
                .withArgs(arguments.expand(args).split(" "))
                .withVolumeMounts(volumeMounts)
                .withNewResources()
                    .withLimits(getResourcesMap(limitMemory, limitCpu))
                    .withRequests(getResourcesMap(requestMemory, requestCpu))
                .endResources()
                .withNewSecurityContext()
                    .withPrivileged(privileged)
                .endSecurityContext()
                .withEnv(envVars)
                .build();

        Map<String, String> labels = new TreeMap<>();
        labels.put(LABEL_KEY, LABEL_VALUE);

        return new PodBuilder()
                .withNewMetadata()
                .withName(agent.getNodeName())
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withVolumes(tempVolumes)
                .withContainers(container)
                .withRestartPolicy("Never")
                .withImagePullSecrets(imagePullSecrets)
                .endSpec()
                .build();
    }

    public Secret buildSecret(String namespace,
                              String secretName,
                              List<DockerRegistryEndpoint> credentials) throws IOException {
        DockerConfigBuilder dockerConfigBuilder = new DockerConfigBuilder(credentials);
        String dockerConfig = dockerConfigBuilder.buildDockercfgForKubernetes();

        Map<String, String> data = new HashMap<>();
        data.put(".dockercfg", dockerConfig);
        return new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespace)
                .endMetadata()
                .withData(data)
                .withType("kubernetes.io/dockercfg")
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

    @DataBoundSetter
    public void setRootFs(final String rootFs) {
        this.rootFs = rootFs;
    }

    public String getRootFs() {
        return rootFs;
    }

    @DataBoundSetter
    public void setRetentionStrategy(final RetentionStrategy<?> retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public RetentionStrategy<?> getRetentionStrategy() {
        return retentionStrategy;
    }

    @DataBoundSetter
    public void setPrivileged(final boolean privileged) {
        this.privileged = privileged;
    }

    public boolean getPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setRequestCpu(final String requestCpu) {
        this.requestCpu = requestCpu;
    }

    public String getRequestCpu() {
        return requestCpu;
    }

    @DataBoundSetter
    public void setRequestMemory(final String requestMemory) {
        this.requestMemory = requestMemory;
    }

    public String getRequestMemory() {
        return requestMemory;
    }

    @DataBoundSetter
    public void setLimitCpu(final String limitCpu) {
        this.limitCpu = limitCpu;
    }

    public String getLimitCpu() {
        return limitCpu;
    }

    @DataBoundSetter
    public void setLimitMemory(final String limitMemory) {
        this.limitMemory = limitMemory;
    }

    public String getLimitMemory() {
        return limitMemory;
    }

    @DataBoundSetter
    public void setEnvVars(List<PodEnvVar> envVars) {
        this.envVars = envVars;
    }

    public List<PodEnvVar> getEnvVars() {
        return envVars;
    }

    @DataBoundSetter
    public void setVolumes(List<PodVolume> volumes) {
        this.volumes = volumes;
    }

    public List<PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setImagePullSecrets(List<PodImagePullSecrets> imagePullSecrets) {
        this.imagePullSecrets = imagePullSecrets;
    }

    public List<PodImagePullSecrets> getImagePullSecrets() {
        return imagePullSecrets;
    }

    @DataBoundSetter
    public void setPrivateRegistryCredentials(List<DockerRegistryEndpoint> privateRegistryCredentials) {
        this.privateRegistryCredentials = privateRegistryCredentials;
    }

    public List<DockerRegistryEndpoint> getPrivateRegistryCredentials() {
        return privateRegistryCredentials;
    }

    private Map<String, Quantity> getResourcesMap(String memory, String cpu) {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity> builder();
        if (StringUtils.isNotBlank(memory)) {
            builder.put("memory", new Quantity(memory + "Mi"));
        }

        if (StringUtils.isNotBlank(cpu)) {
            builder.put("cpu", new Quantity(cpu + "m"));
        }

        return builder.build();
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

        public List<Descriptor<RetentionStrategy<?>>> getKubernetseRetentionStrategyDescriptors() {
            List<Descriptor<RetentionStrategy<?>>> list = new ArrayList<>();
            list.add(KubernetesOnceRetentionStrategy.DESCRIPTOR);
            list.add(KubernetesIdleRetentionStrategy.DESCRIPTOR);
            return list;
        }

        public FormValidation doCheckRequestCpu(@QueryParameter String value) {
            if (value.matches("^[0-9]*$")) {
                return FormValidation.ok();
            }
            return FormValidation.error("Must be number");
        }

        public FormValidation doCheckRequestMemory(@QueryParameter String value) {
            if (value.matches("^[0-9]*$")) {
                return FormValidation.ok();
            }
            return FormValidation.error("Must be number");
        }

        public FormValidation doCheckLimitCpu(@QueryParameter String value) {
            if (value.matches("^[0-9]*$")) {
                return FormValidation.ok();
            }
            return FormValidation.error("Must be number");
        }

        public FormValidation doCheckLimitMemory(@QueryParameter String value) {
            if (value.matches("^[0-9]*$")) {
                return FormValidation.ok();
            }
            return FormValidation.error("Must be number");
        }
    }
}
