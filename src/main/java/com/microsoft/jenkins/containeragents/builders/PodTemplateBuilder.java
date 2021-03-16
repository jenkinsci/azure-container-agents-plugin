package com.microsoft.jenkins.containeragents.builders;


import com.microsoft.jenkins.containeragents.PodTemplate;
import com.microsoft.jenkins.containeragents.remote.LaunchMethodTypeContent;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;
import com.microsoft.jenkins.containeragents.util.Constants;

public class PodTemplateBuilder extends PodTemplateFluent<PodTemplateBuilder> {

    private PodTemplateFluent<?> fluent;

    public PodTemplateBuilder() {
        this.fluent = this;
    }

    public PodTemplateBuilder(PodTemplate template) {
        this.fluent = this;
        this.fluent.withName(template.getName());
        this.fluent.withDescription(template.getDescription());
        this.fluent.withImage(template.getImage());
        this.fluent.withCommand(template.getCommand());
        this.fluent.withArgs(template.getArgs());
        this.fluent.withLabel(template.getLabel());
        this.fluent.withRootFs(template.getRootFs());
        if (template.getRetentionStrategy() instanceof ContainerIdleRetentionStrategy) {
            this.fluent.withIdleRetentionStrategy(((ContainerIdleRetentionStrategy) template.getRetentionStrategy())
                    .getIdleMinutes());
        } else {
            this.fluent.withOnceRetentionStrategy();
        }
        this.fluent.withPrivileged(template.getPrivileged());
        this.fluent.withSpecifyNode(template.getSpecifyNode());
        this.fluent.withRequestCpu(template.getRequestCpu());
        this.fluent.withRequestMemory(template.getRequestMemory());
        this.fluent.withLimitCpu(template.getLimitCpu());
        this.fluent.withLimitMemory(template.getLimitMemory());
        this.fluent.withEnvVars(template.getEnvVars());
        this.fluent.withVolumes(template.getVolumes());
        this.fluent.withImagePullSecrets(template.getImagePullSecrets());
        this.fluent.withPrivateRegistryCredentials(template.getPrivateRegistryCredentials());
        if (template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_JNLP)) {
            this.fluent.withJNLPLaunchMethod();
        } else {
            this.fluent.withSSHLaunchMethod(template.getSshCredentialsId(), template.getSshPort());
        }
    }

    public PodTemplateBuilder(PodTemplateFluent<?> fluent) {
        this.fluent = fluent;
    }

    public PodTemplateBuilder(PodTemplateFluent<?> fluent, PodTemplate template) {
        this.fluent = fluent;
        this.fluent.withName(template.getName());
        this.fluent.withDescription(template.getDescription());
        this.fluent.withImage(template.getImage());
        this.fluent.withCommand(template.getCommand());
        this.fluent.withArgs(template.getArgs());
        this.fluent.withLabel(template.getLabel());
        this.fluent.withRootFs(template.getRootFs());
        if (template.getRetentionStrategy() instanceof ContainerIdleRetentionStrategy) {
            this.fluent.withIdleRetentionStrategy(((ContainerIdleRetentionStrategy) template.getRetentionStrategy())
                    .getIdleMinutes());
        } else {
            this.fluent.withOnceRetentionStrategy();
        }
        this.fluent.withPrivileged(template.getPrivileged());
        this.fluent.withSpecifyNode(template.getSpecifyNode());
        this.fluent.withRequestCpu(template.getRequestCpu());
        this.fluent.withRequestMemory(template.getRequestMemory());
        this.fluent.withLimitCpu(template.getLimitCpu());
        this.fluent.withLimitMemory(template.getLimitMemory());
        this.fluent.withEnvVars(template.getEnvVars());
        this.fluent.withVolumes(template.getVolumes());
        this.fluent.withImagePullSecrets(template.getImagePullSecrets());
        this.fluent.withPrivateRegistryCredentials(template.getPrivateRegistryCredentials());
        if (template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_JNLP)) {
            this.fluent.withJNLPLaunchMethod();
        } else {
            this.fluent.withSSHLaunchMethod(template.getSshCredentialsId(), template.getSshPort());
        }
    }

    public PodTemplate build() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName(fluent.getName());
        podTemplate.setLabel(fluent.getLabel());
        podTemplate.setImage(fluent.getImage());
        podTemplate.setImagePullSecrets(fluent.getImagePullSecrets());
        podTemplate.setPrivateRegistryCredentials(fluent.getPrivateRegistryCredentials());
        podTemplate.setCommand(fluent.getCommand());
        podTemplate.setArgs(fluent.getArgs());
        podTemplate.setRootFs(fluent.getRootFs());
        podTemplate.setEnvVars(fluent.getEnvVars());
        podTemplate.setVolumes(fluent.getVolumes());
        podTemplate.setRetentionStrategy(fluent.getRetentionStrategy());
        podTemplate.setSpecifyNode(fluent.getSpecifyNode());
        podTemplate.setPrivileged(fluent.isPrivileged());
        podTemplate.setRequestCpu(fluent.getRequestCpu());
        podTemplate.setRequestMemory(fluent.getRequestMemory());
        podTemplate.setLimitCpu(fluent.getLimitCpu());
        podTemplate.setLimitMemory(fluent.getLimitMemory());
        podTemplate.setLaunchMethodType(fluent.getLaunchMethodType());
        podTemplate.setLaunchMethodTypeContent(new LaunchMethodTypeContent(fluent.getSshCredentialsId(),
                fluent.getSshPort()));
        return podTemplate;
    }
}
