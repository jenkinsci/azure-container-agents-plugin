package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.azure.util.AzureCredentialUtil;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.junit.Assert.assertNotNull;

/**
 * A template file with the required values to run this test is at the root of this repo
 * in a file called '.env-integration.txt'.
 */
public abstract class AzureContainerRule implements TestRule, MethodRule {

    protected Description testDescription;

    public final String subscriptionId;
    public final String clientId;
    public final String clientSecret;

    public final String cloudName;
    public String location;
    public String resourceGroup;
    public final String credentialsId;
    public final String jenkinsUrl;
    public final String jnlpPort;

    public String image;
    public String privateRegistryUrl;
    public String privateRegistryCredentialsId;

    public AzureBaseCredentials servicePrincipal = null;
    public Azure azureClient = null;

    public AzureContainerRule() {
        subscriptionId = loadProperty("ACS_AGENT_TEST_SUBSCRIPTION_ID");
        clientId = loadProperty("ACS_AGENT_TEST_CLIENT_ID");
        clientSecret = loadProperty("ACS_AGENT_TEST_CLIENT_SECRET");

        cloudName = AzureContainerUtils.generateName("AzureContainerTest", 5);

        credentialsId = UUID.randomUUID().toString();
        jenkinsUrl = loadProperty("ACS_AGENT_TEST_JENKINS_URL", "localhost");
        jnlpPort = loadProperty("ACS_AGENT_TEST_JNLP_PORT", "60000");
    }

    public void before() throws Exception {
        prepareCredentials();
        prepareServicePrincipal();
    }

    public void prepareImage(String imageEnv, String privateRegistryUrlEnv, String privateRegistryNameEnv, String privateRegistryKeyEnv) {
        image = TestUtils.loadProperty(imageEnv, "jenkins/inbound-agent");
        privateRegistryUrl = TestUtils.loadProperty(privateRegistryUrlEnv);

        final String privateRegistryName = TestUtils.loadProperty(privateRegistryNameEnv);
        final String privateRegistryKey = TestUtils.loadProperty(privateRegistryKeyEnv);

        if (StringUtils.isBlank(privateRegistryName) || StringUtils.isBlank(privateRegistryKey)) {
            return;
        }

        StandardUsernamePasswordCredentials privateRegistryCredential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                privateRegistryCredentialsId = UUID.randomUUID().toString(),
                "Private Registry for Test",
                privateRegistryName,
                privateRegistryKey
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(privateRegistryCredential);
    }


    protected void prepareCredentials() throws Exception {
        AzureCredentials azureCredentials = new AzureCredentials(
                CredentialsScope.GLOBAL,
                credentialsId,
                "Azure Credentials for Azure Container Agent Test",
                subscriptionId,
                clientId,
                clientSecret
        );
        azureCredentials.setTenant(loadProperty("ACS_AGENT_TEST_TENANT_ID"));

        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(azureCredentials);
    }

    protected void prepareServicePrincipal() throws Exception {
        servicePrincipal = AzureCredentialUtil.getCredential(null, credentialsId);
        assertNotNull(servicePrincipal);
        azureClient = AzureContainerUtils.getAzureClient(credentialsId);
        assertNotNull(azureClient);
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
