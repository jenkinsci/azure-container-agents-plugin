package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.core.credentials.TokenCredentialData;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.Serializable;
import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.junit.Assert.assertNotNull;

/**
 * A template file with the required values to run this test is at the root of this repo
 * in a file called '.env-integration.txt'.
 */
public abstract class AzureContainerRule implements TestRule, MethodRule {

    protected Description testDescription;

    public Azure azureClient = null;

    public AzureContainerData containerData = new AzureContainerData();

    public static class AzureContainerData implements Serializable {
        public final String credentialsId = UUID.randomUUID().toString();
        public final String subscriptionId = System.getenv("ACS_AGENT_TEST_SUBSCRIPTION_ID");
        public final String clientId = System.getenv("ACS_AGENT_TEST_CLIENT_ID");
        public final String clientSecret = System.getenv("ACS_AGENT_TEST_CLIENT_SECRET");;
        public final String tenantId = loadProperty("ACS_AGENT_TEST_TENANT_ID");

        public final String cloudName = AzureContainerUtils.generateName("AzureContainerTest", 5);
        public String location = loadProperty("ACI_AGENT_TEST_AZURE_LOCATION", "East US");
        public String resourceGroup = AzureContainerUtils.generateName(loadProperty("ACI_AGENT_TEST_RESOURCE_GROUP", "AzureContainerTest"), 3);
        public final String jenkinsUrl = loadProperty("ACS_AGENT_TEST_JENKINS_URL", "localhost");
        public final String jnlpPort = loadProperty("ACS_AGENT_TEST_JNLP_PORT", "60000");

        public String image;
        public String privateRegistryUrl;
        public String privateRegistryCredentialsId;

    }

    public void before() throws Exception {
        prepareServicePrincipal();
    }

    public void prepareImage(String imageEnv, String privateRegistryUrlEnv, String privateRegistryNameEnv, String privateRegistryKeyEnv) {
        containerData.image = TestUtils.loadProperty(imageEnv, "jenkins/inbound-agent");
        containerData.privateRegistryUrl = TestUtils.loadProperty(privateRegistryUrlEnv);

        final String privateRegistryName = TestUtils.loadProperty(privateRegistryNameEnv);
        final String privateRegistryKey = TestUtils.loadProperty(privateRegistryKeyEnv);

        if (StringUtils.isBlank(privateRegistryName) || StringUtils.isBlank(privateRegistryKey)) {
            return;
        }

        StandardUsernamePasswordCredentials privateRegistryCredential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                containerData.privateRegistryCredentialsId = UUID.randomUUID().toString(),
                "Private Registry for Test",
                privateRegistryName,
                privateRegistryKey
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(privateRegistryCredential);
    }


    private AzureCredentials getServicePrincipalCredentials() throws Exception {
        AzureCredentials azureCredentials = new AzureCredentials(
                CredentialsScope.GLOBAL,
                containerData.credentialsId,
                "Azure Credentials for Azure Container Agent Test",
                containerData.subscriptionId,
                containerData.clientId,
                containerData.clientSecret
        );
        azureCredentials.setTenant(containerData.tenantId);
        return azureCredentials;
    }

    protected void prepareServicePrincipal() throws Exception {
        TokenCredentialData tokenCredentialData = TokenCredentialData.deserialize(getServicePrincipalCredentials().serializeToTokenData());
        this.azureClient = AzureContainerUtils.getClient(tokenCredentialData);
    }

    public void after() throws Exception {

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
