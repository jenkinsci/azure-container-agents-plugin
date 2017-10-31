package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.TokenCache;
import org.junit.Assert;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;

public abstract class AzureContainerRule implements TestRule, MethodRule {

    protected Description testDescription;

    public final String subscriptionId;
    public final String clientId;
    public final String clientSecret;
    public final String oauth2TokenEndpoint;
    public final String serviceManagementURL;
    public final String authenticationEndpoint;
    public final String resourceManagerEndpoint;
    public final String graphEndpoint;

    public final String cloudName;
    public final String location;
    public final String resourceGroup;
    public final String credentialsId;

    public AzureCredentials.ServicePrincipal servicePrincipal = null;

    public AzureContainerRule() {
        subscriptionId = loadProperty("ACS_AGENT_TEST_SUBSCRIPTION_ID");
        clientId = loadProperty("ACS_AGENT_TEST_CLIENT_ID");
        clientSecret = loadProperty("ACS_AGENT_TEST_CLIENT_SECRET");
        oauth2TokenEndpoint = "https://login.windows.net/" + loadProperty("ACS_AGENT_TEST_TENANT");
        serviceManagementURL = loadProperty("ACS_AGENT_TEST_AZURE_MANAGEMENT_URL", "https://management.core.windows.net/");
        authenticationEndpoint = loadProperty("ACS_AGENT_TEST_AZURE_AUTH_URL", "https://login.microsoftonline.com/");
        resourceManagerEndpoint = loadProperty("ACS_AGENT_TEST_AZURE_RESOURCE_URL", "https://management.azure.com/");
        graphEndpoint = loadProperty("ACS_AGENT_TEST_AZURE_GRAPH_URL", "https://graph.windows.net/");

        cloudName = AzureContainerUtils.generateName("AciCloudTest", 5);
        location = loadProperty("ACS_AGENT_TEST_AZURE_LOCATION", "East US");
        resourceGroup = AzureContainerUtils.generateName(loadProperty("ACS_AGENT_TEST_RESOURCE_GROUP", "AzureContainerTest"), 3);
        credentialsId = UUID.randomUUID().toString();
    }

    public void before() throws Exception {
        prepareCredentials();
        prepareServicePrincipal();
        prepareResourceGroup();
    }

    protected void prepareCredentials() throws Exception {
        AzureCredentials azureCredentials = new AzureCredentials(
                CredentialsScope.GLOBAL,
                credentialsId,
                "Azure Credentials for Azure Container Agent Test",
                subscriptionId,
                clientId,
                clientSecret,
                oauth2TokenEndpoint,
                serviceManagementURL,
                authenticationEndpoint,
                resourceManagerEndpoint,
                graphEndpoint
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(azureCredentials);
    }

    protected void prepareServicePrincipal() throws Exception {
        servicePrincipal = AzureCredentials.getServicePrincipal(credentialsId);

        Assert.assertNotNull(servicePrincipal);
    }


    public void prepareResourceGroup() throws Exception {
        Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
        ResourceGroup rg = azureClient.resourceGroups().getByName(resourceGroup);
        if (rg == null) {
            rg = azureClient.resourceGroups().define(resourceGroup).withRegion(location).create();
        }

        Assert.assertNotNull(rg);
    }

    public void after() throws Exception {
        cleanResourceGroup();
    }

    public void cleanResourceGroup() throws Exception {
        Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
        azureClient.resourceGroups().deleteByName(resourceGroup);

        Assert.assertNull(azureClient.resourceGroups().getByName(resourceGroup));
    }

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return apply(base, Description.createTestDescription(method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations()));
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                testDescription = description;
                before();
                try {
                    base.evaluate();
                } finally {
                    after();
                    testDescription = null;
                }
            }
        };
    }

}
