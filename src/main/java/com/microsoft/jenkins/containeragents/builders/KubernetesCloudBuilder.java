package com.microsoft.jenkins.containeragents.builders;


import com.microsoft.jenkins.containeragents.KubernetesCloud;
import com.microsoft.jenkins.containeragents.PodTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KubernetesCloudBuilder {

    private String cloudName;

    private String acsCredentialsId;

    private String azureCredentialsId;

    private String resourceGroup;

    private String serviceName;

    private String namespace;

    private int startupTimeout;

    private List<PodTemplate> templates;

    //CHECKSTYLE:OFF
    public KubernetesCloudBuilder() {
        this.namespace = "default";
        this.startupTimeout = 10;
        this.templates = new ArrayList<>();
    }

    public KubernetesCloudBuilder withCloudName(String cloudName) {
        this.cloudName = cloudName;
        return this;
    }

    public KubernetesCloudBuilder withAzureCredentialsId(String azureCredentialsId) {
        this.azureCredentialsId = azureCredentialsId;
        return this;
    }

    public KubernetesCloudBuilder withResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
        return this;
    }

    public KubernetesCloudBuilder withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public KubernetesCloudBuilder withNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public KubernetesCloudBuilder withAcsCredentialsId(String acsCredentialsId) {
        this.acsCredentialsId = acsCredentialsId;
        return this;
    }

    public KubernetesCloudBuilder withStartupTimeout(int startupTimeout) {
        this.startupTimeout = startupTimeout;
        return this;
    }

    public KubernetesCloudBuilder addToTemplates(PodTemplate... templates) {
        Collections.addAll(this.templates, templates);
        return this;
    }

    public PodTemplateNested addNewTemplate() {
        return new PodTemplateNested();
    }

    public PodTemplateNested addNewTemplateLike(PodTemplate template) {
        return new PodTemplateNested(template);
    }

    //CHECKSTYLE:ON

    public KubernetesCloud build() {
        KubernetesCloud cloud = new KubernetesCloud(this.cloudName);
        cloud.setAcsCredentialsId(this.acsCredentialsId);
        cloud.setAzureCredentialsId(this.azureCredentialsId);
        cloud.setNamespace(this.namespace);
        cloud.setResourceGroup(this.resourceGroup);
        cloud.setServiceName(this.serviceName);
        cloud.setStartupTimeout(this.startupTimeout);
        cloud.setTemplates(this.templates);
        return cloud;
    }

    public class PodTemplateNested extends PodTemplateFluent<PodTemplateNested> {

        private final PodTemplateBuilder builder;

        PodTemplateNested() {
            this.builder = new PodTemplateBuilder(this);
        }

        PodTemplateNested(PodTemplate template) {
            this.builder = new PodTemplateBuilder(this, template);
        }

        public KubernetesCloudBuilder endTemplate() {
            return KubernetesCloudBuilder.this.addToTemplates(builder.build());
        }
    }
}
