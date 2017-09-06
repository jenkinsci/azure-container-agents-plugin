package com.microsoft.jenkins.containeragents.builders;

import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;

public class AciContainerTemplateBuilder extends AciContainerTemplateFluent<AciContainerTemplateBuilder> {

    private AciContainerTemplateFluent<?> fluent;

    public AciContainerTemplateBuilder() {
        this.fluent = this;
    }

    public AciContainerTemplateBuilder(AciContainerTemplateFluent<?> fluent) {
        this.fluent = fluent;
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
