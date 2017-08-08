package com.microsoft.azure.containeragents.aci;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AciCloud extends Cloud {
    private static final Logger LOGGER = Logger.getLogger(AciCloud.class.getName());

    private String credentialsId;

    private String resourceGroup;

    private List<AciContainer> templates;

    @DataBoundConstructor
    public AciCloud(String name,
                    String credentialsId,
                    String resourceGroup,
                    List<AciContainer> templates) {
        super(name);
        this.credentialsId = credentialsId;
        this.resourceGroup = resourceGroup;
        this.templates = templates;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {

    }

    @Override
    public boolean canProvision(Label label) {
        return true;
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

    public List<AciContainer> getTemplates() {
        return templates;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Azure Container Instance";
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.add("--- Select Azure Credentials ---", "");
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(AzureCredentials.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public ListBoxModel doFillResourceGroupItems(@QueryParameter String azureCredentialsId) throws IOException {
            ListBoxModel model = new ListBoxModel();
            model.add("--- Select Resource Group ---", "");
            if (StringUtils.isBlank(azureCredentialsId)) {
                return model;
            }

            try {
                AzureCredentials.ServicePrincipal servicePrincipal
                        = AzureCredentials.getServicePrincipal(azureCredentialsId);
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
