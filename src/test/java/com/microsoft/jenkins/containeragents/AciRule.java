package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.storage.StorageAccount;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;

import java.io.Serializable;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertNull;


public class AciRule extends AzureContainerRule {

    private static final Logger LOGGER = Logger.getLogger(AciRule.class.getName());

    public AciData data = new AciData();

    public static class AciData implements Serializable {
        public String label = AzureContainerUtils.generateName("AciTemplateTest",3);
        public String storageAccountCredentialsId;
        public String fileShareName;
        public AzureStorageAccount.StorageAccountCredential storageAccountCredential;
    }

    public AciRule() {
        super();
    }

    @Override
    public void before() throws Exception {
        super.before();
        prepareResourceGroup();
        prepareStorageAccount();
        prepareImage("ACI_AGENT_TEST_IMAGE",
                "ACI_AGENT_TEST_REGISTRY_URL",
                "ACI_AGENT_TEST_REGISTRY_NAME",
                "ACI_AGENT_TEST_REGISTRY_KEY");
    }

    @Override
    public void after() throws Exception {
        super.after();
        cleanResourceGroup();
    }

    public void prepareResourceGroup() throws Exception {
        String resourceGroup = containerData.resourceGroup;
        ResourceGroup rg = azureClient.resourceGroups().getByName(resourceGroup);
        if (rg == null) {
            LOGGER.info("Creating resource group: " + resourceGroup);
            rg = azureClient.resourceGroups().define(resourceGroup).withRegion(containerData.location).create();
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
                .withRegion(containerData.location)
                .withExistingResourceGroup(containerData.resourceGroup)
                .create();
        LOGGER.info("Created storage account: " + containerData.resourceGroup);

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

    public void cleanResourceGroup() throws Exception {
        String resourceGroup = containerData.resourceGroup;
        LOGGER.info("Deleting resource group: " + resourceGroup);
        azureClient.resourceGroups().deleteByName(resourceGroup);
        LOGGER.info("Deleted resource group: " + resourceGroup);

        assertNull(azureClient.resourceGroups().getByName(resourceGroup));
    }
}
