package com.microsoft.jenkins.containeragents.builders;

import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AciCloudBuilder {

    private String cloudName;

    private String credentialsId;

    private String resourceGroup;

    private List<AciContainerTemplate> templates;

    public AciCloudBuilder() {
        templates = new ArrayList<>();
    }

    //CHECKSTYLE:OFF
    public AciCloudBuilder withCloudName(String cloudName) {
        this.cloudName = cloudName;
        return this;
    }

    public AciCloudBuilder withAzureCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
        return this;
    }

    public AciCloudBuilder withResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
        return this;
    }

    public AciCloudBuilder addToTemplates(AciContainerTemplate... templates) {
        this.templates.addAll(Arrays.asList(templates));
        return this;
    }

    public AciContainerTemplateNested addNewTemplate() {
        return new AciContainerTemplateNested();
    }

    public AciContainerTemplateNested addNewTemplateLike(AciContainerTemplate template) {
        return new AciContainerTemplateNested(template);
    }
    //CHECKSTYLE:ON

    public AciCloud build() {
        return new AciCloud(cloudName, credentialsId, resourceGroup, templates,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public class AciContainerTemplateNested extends
        AciContainerTemplateFluent<AciContainerTemplateNested> {

        private final AciContainerTemplateBuilder builder;

        public AciContainerTemplateNested() {
            this.builder = new AciContainerTemplateBuilder(this);
        }

        public AciContainerTemplateNested(AciContainerTemplate template) {
            this.builder = new AciContainerTemplateBuilder(this, template);
        }

        public AciCloudBuilder endTemplate() {
            return AciCloudBuilder.this.addToTemplates(builder.build());
        }
    }
}
