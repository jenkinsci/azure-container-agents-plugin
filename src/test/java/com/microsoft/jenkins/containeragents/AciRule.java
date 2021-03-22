package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.storage.StorageAccount;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.azurecommons.core.credentials.TokenCredentialData;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.Serializable;
import java.util.UUID;
import java.util.logging.Logger;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.junit.Assert.assertNull;


public class AciRule implements TestRule, MethodRule {

    private static final Logger LOGGER = Logger.getLogger(AciRule.class.getName());

    public AciData data = new AciData();
    protected Description testDescription;

    public Azure azureClient = null;

    public static class AciData implements Serializable {
        public String label = AzureContainerUtils.generateName("AciTemplateTest",3);
        public String storageAccountCredentialsId;
        public String fileShareName;
        public AzureStorageAccount.StorageAccountCredential storageAccountCredential;

        public final SimpleServicePrincipal servicePrincipal = new SimpleServicePrincipal();

        public final String cloudName = AzureContainerUtils.generateName("AzureContainerTest", 5);
        public String location = loadProperty("ACI_AGENT_TEST_AZURE_LOCATION", "East US");
        public String resourceGroup = AzureContainerUtils.generateName(loadProperty("ACI_AGENT_TEST_RESOURCE_GROUP", "AzureContainerTest"), 3);

        public String image;
        public String privateRegistryUrl;
        public String privateRegistryCredentialsId;
    }

    public void before() throws Exception {
        prepareServicePrincipal();
        prepareResourceGroup();
        prepareStorageAccount();
        prepareImage("ACI_AGENT_TEST_IMAGE",
                "ACI_AGENT_TEST_REGISTRY_URL",
                "ACI_AGENT_TEST_REGISTRY_NAME",
                "ACI_AGENT_TEST_REGISTRY_KEY");
    }

    protected void prepareServicePrincipal() {
        TokenCredentialData tokenCredentialData = TokenCredentialData.deserialize(getServicePrincipalCredentials().serializeToTokenData());
        this.azureClient = AzureContainerUtils.getClient(tokenCredentialData);
    }

    public void prepareImage(String imageEnv, String privateRegistryUrlEnv, String privateRegistryNameEnv, String privateRegistryKeyEnv) {
        data.image = TestUtils.loadProperty(imageEnv, "jenkins/inbound-agent");
        data.privateRegistryUrl = TestUtils.loadProperty(privateRegistryUrlEnv);

        final String privateRegistryName = TestUtils.loadProperty(privateRegistryNameEnv);
        final String privateRegistryKey = TestUtils.loadProperty(privateRegistryKeyEnv);

        if (StringUtils.isBlank(privateRegistryName) || StringUtils.isBlank(privateRegistryKey)) {
            return;
        }

        StandardUsernamePasswordCredentials privateRegistryCredential = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                data.privateRegistryCredentialsId = UUID.randomUUID().toString(),
                "Private Registry for Test",
                privateRegistryName,
                privateRegistryKey
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(privateRegistryCredential);
    }

    private AzureCredentials getServicePrincipalCredentials() {
        AzureCredentials azureCredentials = new AzureCredentials(
                CredentialsScope.GLOBAL,
                data.servicePrincipal.credentialsId,
                "Azure Credentials for Azure Container Agent Test",
                data.servicePrincipal.subscriptionId,
                data.servicePrincipal.clientId,
                data.servicePrincipal.clientSecret
        );
        azureCredentials.setTenant(data.servicePrincipal.tenantId);
        return azureCredentials;
    }


    public void after() {
        cleanResourceGroup();
    }

    public void prepareResourceGroup() {
        String resourceGroup = data.resourceGroup;
        ResourceGroup rg = azureClient.resourceGroups().getByName(resourceGroup);
        if (rg == null) {
            LOGGER.info("Creating resource group: " + resourceGroup);
            rg = azureClient.resourceGroups().define(resourceGroup).withRegion(data.location).create();
            LOGGER.info("Created resource group: " + resourceGroup);
        }

        Assert.assertNotNull(rg);
    }

    public void prepareStorageAccount() throws Exception {
        String accountName;
        StorageAccountKey accountKey;

        String randomString = RandomStringUtils.random(8, "abcdfghjklmnpqrstvwxz0123456789");
        LOGGER.info("Creating storage account: " + randomString);
        StorageAccount storageAccount = azureClient.storageAccounts()
                .define(accountName = randomString)
                .withRegion(data.location)
                .withExistingResourceGroup(data.resourceGroup)
                .create();
        LOGGER.info("Created storage account: " + data.resourceGroup);

        accountKey = storageAccount.getKeys().get(0);

        final String storageConnectionString = "DefaultEndpointsProtocol=https;" +
                "AccountName=" + accountName + ";" +
                "AccountKey=" + accountKey.value();

        CloudStorageAccount cloudStorageAccount = CloudStorageAccount.parse(storageConnectionString);
        CloudFileClient fileClient = cloudStorageAccount.createCloudFileClient();
        String theFileShareName = RandomStringUtils.random(8, "abcdfghjklmnpqrstvwxz0123456789");
        CloudFileShare share = fileClient.getShareReference(data.fileShareName = theFileShareName);
        LOGGER.info("Creating file share: " + theFileShareName);
        Assert.assertTrue(share.createIfNotExists());
        LOGGER.info("Created file share: " + theFileShareName);

        createAzureStorageCredential(data.storageAccountCredentialsId = UUID.randomUUID().toString(),
                accountName,
                accountKey.value());
    }

    public void createAzureStorageCredential(String credentialsId, String accountName, String accountKey) {
        this.data.storageAccountCredential = new AzureStorageAccount(CredentialsScope.GLOBAL,
                credentialsId,
                "Storage Credential for Test",
                accountName,
                accountKey,
                "").getStorageCred();
    }

    public void cleanResourceGroup() {
        String resourceGroup = data.resourceGroup;
        LOGGER.info("Deleting resource group: " + resourceGroup);
        azureClient.resourceGroups().deleteByName(resourceGroup);
        LOGGER.info("Deleted resource group: " + resourceGroup);

        assertNull(azureClient.resourceGroups().getByName(resourceGroup));
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
