package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.storage.StorageAccount;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.azure.storage.file.CloudFileShare;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.builders.AciCloudBuilder;
import com.microsoft.jenkins.containeragents.builders.AciContainerTemplateBuilder;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;

import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;


public class AciRule extends AzureContainerRule {

    public AciContainerTemplate template = null;
    public AciCloud cloud = null;
    public String storageAccountCredentialsId;
    public String fileShareName;
    public String label;

    public AciRule() {
        super();
        location = loadProperty("ACI_AGENT_TEST_AZURE_LOCATION", "East US");
        resourceGroup = AzureContainerUtils.generateName(loadProperty("ACI_AGENT_TEST_RESOURCE_GROUP", "AzureContainerTest"), 3);
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
        prepareTemplate();
        prepareCloud();
    }

    @Override
    public void after() throws Exception {
        super.after();
        cleanResourceGroup();
    }

    public void prepareResourceGroup() throws Exception {
        Azure azureClient = AzureContainerUtils.getAzureClient(credentialsId);
        ResourceGroup rg = azureClient.resourceGroups().getByName(resourceGroup);
        if (rg == null) {
            rg = azureClient.resourceGroups().define(resourceGroup).withRegion(location).create();
        }

        Assert.assertNotNull(rg);
    }

    public void prepareStorageAccount() throws Exception {
        String accountName;
        StorageAccountKey accountKey;

        StorageAccount storageAccount = azureClient.storageAccounts()
                .define(accountName = RandomStringUtils.random(8, "abcdfghjklmnpqrstvwxz0123456789"))
                .withRegion("East US")
                .withExistingResourceGroup(resourceGroup)
                .create();

        accountKey = storageAccount.getKeys().get(0);

        final String storageConnectionString = "DefaultEndpointsProtocol=http;" +
                "AccountName=" + accountName + ";" +
                "AccountKey=" + accountKey.value();

        CloudStorageAccount cloudStorageAccount = CloudStorageAccount.parse(storageConnectionString);
        CloudFileClient fileClient = cloudStorageAccount.createCloudFileClient();
        CloudFileShare share = fileClient.getShareReference(fileShareName = RandomStringUtils.random(8, "abcdfghjklmnpqrstvwxz0123456789"));
        Assert.assertTrue(share.createIfNotExists());

        createAzureStorageCredential(storageAccountCredentialsId = UUID.randomUUID().toString(),
                accountName,
                accountKey.value());
    }

    public void createAzureStorageCredential(String credentialsId, String accountName, String accountKey) {
        AzureCredentials storageCredential = new AzureCredentials(CredentialsScope.GLOBAL,
                credentialsId,
                "Storage Credential for Test",
                accountName,
                accountKey,
                "");
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(storageCredential);
    }

    public void prepareCloud() throws Exception {
        cloud = new AciCloudBuilder().withCloudName(cloudName)
                .withAzureCredentialsId(credentialsId)
                .withResourceGroup(resourceGroup)
                .addToTemplates(template)
                .build();
    }

    public void prepareTemplate() throws Exception {
        template =  new AciContainerTemplateBuilder()
                .withName(AzureContainerUtils.generateName("AciTemplate", 5))
                .withLabel(label = AzureContainerUtils.generateName("AciTemplateTest",3))
                .addNewEnvVar("ENV", "echo pass")
                .addNewPort("8080")
                .addNewAzureFileVolume("/afs", fileShareName, storageAccountCredentialsId)
                .withImage(image)
                .addNewPrivateRegistryCredential(privateRegistryUrl, privateRegistryCredentialsId)
                .withIdleRetentionStrategy(60)
                .build();

        Assert.assertNotNull(template);
    }

    public void cleanResourceGroup() throws Exception {
        Azure azureClient = AzureContainerUtils.getAzureClient(credentialsId);
        azureClient.resourceGroups().deleteByName(resourceGroup);

        Assert.assertNull(azureClient.resourceGroups().getByName(resourceGroup));
    }
}
