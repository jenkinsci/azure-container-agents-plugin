package com.microsoft.azure.containeragents.aci;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AciCloud extends Cloud {
    private static final Logger LOGGER = Logger.getLogger(AciCloud.class.getName());

    private String credentialsId;

    private String resourceGroup;

    private List<AciContainerTemplate> templates;

    @DataBoundConstructor
    public AciCloud(String name,
                    String credentialsId,
                    String resourceGroup,
                    List<AciContainerTemplate> templates) {
        super(name);
        this.credentialsId = credentialsId;
        this.resourceGroup = resourceGroup;
        this.templates = templates;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            LOGGER.log(Level.INFO, "Start ACI container for label {0} workLoad {1}",
                    new Object[] {label, excessWorkload});
            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            AciContainerTemplate template = getFirstTemplate(label);
            LOGGER.log(Level.INFO, "Using ACI Container template: {0}", template.getName());
            for (int i = 1; i <= excessWorkload; i++) {
                r.add(new NodeProvisioner.PlannedNode(template.getName(), Computer.threadPoolForRemoting.submit(
                        new Callable<Node>() {

                            @Override
                            public Node call() throws Exception {
                                AciAgent agent = null;

                                try {
                                    agent = new AciAgent(AciCloud.this, template);

                                    LOGGER.log(Level.INFO, "Add ACI node: {0}", agent.getNodeName());
                                    Jenkins.getInstance().addNode(agent);

                                    //Deploy ACI
                                    template.provisionAgents(AciCloud.this, agent);

                                    //wait to JNLP online
                                    waitToOnline(agent);

                                    return agent;
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, e.toString());
                                    throw new Exception(e);
                                }

                            }
                        }
                ), 1));
            }

            return r;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canProvision(Label label) {
        return getFirstTemplate(label) != null;
    }

    public AciContainerTemplate getFirstTemplate(Label label) {
        for (AciContainerTemplate template : templates) {
            if (label == null || label.matches(template.getLabelSet())) {
                return template;
            }
        }
        return null;
    }

    private void waitToOnline(AciAgent agent) throws IllegalStateException, InterruptedException {
        LOGGER.log(Level.INFO, "Waiting agent {0} to online", agent.getNodeName());

        while (true) {
            if (agent.toComputer() == null) {
                throw new IllegalStateException("ACI container has deleted");
            }
            if (agent.toComputer().isOnline()) {
                break;
            }
            final int retryInterval = 5 * 1000;
            Thread.sleep(retryInterval);
        }
    }

    public String getName() {
        return name;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    public List<AciContainerTemplate> getTemplates() {
        return templates;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Instance";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.add("--- Select Azure Credentials ---", "");
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillResourceGroupItems(@QueryParameter String credentialsId) throws IOException {
            ListBoxModel model = new ListBoxModel();
            model.add("--- Select Resource Group ---", "");
            if (StringUtils.isBlank(credentialsId)) {
                return model;
            }

            try {
                AzureCredentials.ServicePrincipal servicePrincipal
                        = AzureCredentials.getServicePrincipal(credentialsId);
                final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

                List<ResourceGroup> list = azureClient.resourceGroups().list();
                for (ResourceGroup resourceGroup : list) {
                    model.add(resourceGroup.name());
                }
                return model;
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Cannot list resource group name: {}", e);
                return model;
            }
        }
    }
}
