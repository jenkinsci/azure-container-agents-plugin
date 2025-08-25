package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import hudson.ExtensionList;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

import java.util.List;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Contains quick smoke test(s) that can be used to prove classloading works
 * Uses a {@link RealJenkinsExtension} to use an actual Jenkins instance.
 */
class AciCloudQuickIT {

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension();

    @Test
    void testFetchResourceGroups() throws Throwable {
        String expectedResourceGroup = loadProperty("ACI_EXPECTED_RESOURCE_GROUP");
        assertThat(expectedResourceGroup, is(not(emptyString())));

        rr.then(new FetchResourceGroupsOK(new SimpleServicePrincipal(), expectedResourceGroup));
    }

    private static class FetchResourceGroupsOK implements RealJenkinsExtension.Step {

        private final SimpleServicePrincipal servicePrincipal;
        private final String expectedResourceGroup;

        public FetchResourceGroupsOK(SimpleServicePrincipal servicePrincipal, String expectedResourceGroup) {
            this.servicePrincipal = servicePrincipal;
            this.expectedResourceGroup = expectedResourceGroup;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            setup();

            AciCloud.DescriptorImpl aciCloud = ExtensionList.lookupSingleton(AciCloud.DescriptorImpl.class);
            ListBoxModel resourceGroupItems = aciCloud.doFillResourceGroupItems(servicePrincipal.credentialsId);

            List<String> resourceGroups = resourceGroupItems
                    .stream()
                    .map(option -> option.name)
                    .toList();

            assertThat(resourceGroups, hasItem(expectedResourceGroup));
        }

        private void setup() {
            List<Credentials> credentials = SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global());
            AzureCredentials azureCredentials = new AzureCredentials(
                    CredentialsScope.GLOBAL,
                    servicePrincipal.credentialsId,
                    "Azure Credentials for Azure Container Agent Test",
                    servicePrincipal.subscriptionId,
                    servicePrincipal.clientId,
                    Secret.fromString(servicePrincipal.clientSecret)
            );
            azureCredentials.setTenant(servicePrincipal.tenantId);
            credentials.add(azureCredentials);
        }
    }
}
