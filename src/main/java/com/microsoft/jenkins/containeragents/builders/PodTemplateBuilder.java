package com.microsoft.jenkins.containeragents.builders;


import com.microsoft.jenkins.containeragents.PodTemplate;

public class PodTemplateBuilder extends PodTemplateFluent<PodTemplateBuilder> {

    private PodTemplateFluent<?> fluent;

    public PodTemplateBuilder() {
        this.fluent = this;
    }

    public PodTemplateBuilder(PodTemplateFluent<?> fluent) {
        this.fluent = fluent;
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
        return podTemplate;
    }
}
