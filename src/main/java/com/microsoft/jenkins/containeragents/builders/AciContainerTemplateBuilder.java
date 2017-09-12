package com.microsoft.jenkins.containeragents.builders;

import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;

public class AciContainerTemplateBuilder extends AciContainerTemplateFluent<AciContainerTemplateBuilder> {

    private AciContainerTemplateFluent<?> fluent;

    public AciContainerTemplateBuilder() {
        this.fluent = this;
    }

    public AciContainerTemplateBuilder(AciContainerTemplate template) {
        this.fluent = this;
        this.fluent.withName(template.getName());
        this.fluent.withLabel(template.getLabel());
        this.fluent.withImage(template.getImage());
        this.fluent.withOsType(template.getOsType());
        this.fluent.withCommand(template.getCommand());
        this.fluent.withRootFs(template.getRootFs());
        this.fluent.withTimeout(template.getTimeout());
        this.fluent.withPorts(template.getPorts());
        this.fluent.withCpu(template.getCpu());
        this.fluent.withMemory(template.getMemory());
        if (template.getRetentionStrategy() instanceof ContainerIdleRetentionStrategy) {
            this.fluent.withIdleRetentionStrategy(((ContainerIdleRetentionStrategy) template.getRetentionStrategy())
                    .getIdleMinutes());
        } else {
            this.fluent.withOnceRetentionStrategy();
        }
        this.fluent.withEnvVars(template.getEnvVars());
        this.fluent.withPrivateRegistryCredentials(template.getPrivateRegistryCredentials());
        this.fluent.withVolume(template.getVolumes());
    }

    public AciContainerTemplateBuilder(AciContainerTemplateFluent<?> fluent) {
        this.fluent = fluent;
    }

    public AciContainerTemplateBuilder(AciContainerTemplateFluent<?> fluent, AciContainerTemplate template) {
        this.fluent = fluent;
        this.fluent.withName(template.getName());
        this.fluent.withLabel(template.getLabel());
        this.fluent.withImage(template.getImage());
        this.fluent.withOsType(template.getOsType());
        this.fluent.withCommand(template.getCommand());
        this.fluent.withRootFs(template.getRootFs());
        this.fluent.withTimeout(template.getTimeout());
        this.fluent.withPorts(template.getPorts());
        this.fluent.withCpu(template.getCpu());
        this.fluent.withMemory(template.getMemory());
        if (template.getRetentionStrategy() instanceof ContainerIdleRetentionStrategy) {
            this.fluent.withIdleRetentionStrategy(((ContainerIdleRetentionStrategy) template.getRetentionStrategy())
                    .getIdleMinutes());
        } else {
            this.fluent.withOnceRetentionStrategy();
        }
        this.fluent.withEnvVars(template.getEnvVars());
        this.fluent.withPrivateRegistryCredentials(template.getPrivateRegistryCredentials());
        this.fluent.withVolume(template.getVolumes());
    }

    public AciContainerTemplate build() {
        return new AciContainerTemplate(fluent.getName(),
                fluent.getLabel(),
                fluent.getTimeout(),
                fluent.getOsType(),
                fluent.getImage(),
                fluent.getCommand(),
                fluent.getRootFs(),
                fluent.getPorts(),
                fluent.getPrivateRegistryCredentials(),
                fluent.getEnvVars(),
                fluent.getVolumes(),
                fluent.getRetentionStrategy(),
                fluent.getCpu(),
                fluent.getMemory());
    }
}
