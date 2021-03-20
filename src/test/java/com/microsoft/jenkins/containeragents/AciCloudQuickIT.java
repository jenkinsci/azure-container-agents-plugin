package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import hudson.ExtensionList;
import hudson.util.ListBoxModel;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

import java.util.List;
import java.util.stream.Collectors;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Contains quick smoke test(s) that can be used to prove classloading works
 * Uses a {@link RealJenkinsRule} to use an actual Jenkins instance.
 */
public class AciCloudQuickIT {

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void testFetchResourceGroups() throws Throwable {
        String expectedResourceGroup = loadProperty("ACI_EXPECTED_RESOURCE_GROUP");
        assertThat(expectedResourceGroup, is(not(emptyString())));

        rr.then(new FetchResourceGroupsOK(new SimpleServicePrincipal(), expectedResourceGroup));
    }

    private static class FetchResourceGroupsOK implements RealJenkinsRule.Step {

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
                    .collect(Collectors.toList());

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
                    servicePrincipal.clientSecret
            );
            azureCredentials.setTenant(servicePrincipal.tenantId);
            credentials.add(azureCredentials);
        }
    }
}
