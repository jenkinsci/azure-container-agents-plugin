package com.microsoft.jenkins.containeragents;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.microsoft.azure.management.Azure;
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
import com.microsoft.jenkins.containeragents.util.TokenCache;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureCredentials;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

import java.util.UUID;


public class AciRule extends AzureContainerRule {

    public AciContainerTemplate template = null;
    public AciCloud cloud = null;
    public String storageAccountCredentialsId;
    public String fileShareName;
    public String image;
    public String privateRegistryUrl;
    public String privateRegistryCredentialsId;


    public AciRule() {
        super();
        image = TestUtils.loadProperty("ACS_AGENT_TEST_IMAGE", "jenkinsci/jnlp-slave");
        privateRegistryUrl = TestUtils.loadProperty("ACS_AGENT_TEST_REGISTRY_URL");
    }

    @Override
    public void before() throws Exception {
        super.before();
        prepareStorageAccount();
        preparePrivateRegistry();
        prepareTemplate();
        prepareCloud();
    }

    @Override
    public void after() throws Exception {
        super.after();
    }

    public void prepareStorageAccount() throws Exception {
        Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
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

    public void preparePrivateRegistry() {
        final String privateRegistryName = TestUtils.loadProperty("ACS_AGENT_TEST_REGISTRY_NAME");
        final String privateRegistryKey = TestUtils.loadProperty("ACS_AGENT_TEST_REGISTRY_KEY");

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


    public void prepareCloud() throws Exception {
        cloud = new AciCloudBuilder().withCloudName(this.cloudName)
                .withAzureCredentialsId(this.credentialsId)
                .withResourceGroup(this.resourceGroup)
                .build();
    }

    public void prepareTemplate() throws Exception {
        template =  new AciContainerTemplateBuilder()
                .withName(AzureContainerUtils.generateName("AciTemplate", 5))
                .withLabel("AciTemplateTest")
                .addNewEnvVar("ENV", "echo pass")
                .addNewPort("8080")
                .addNewAzureFileVolume("/afs", fileShareName, storageAccountCredentialsId)
                .withImage(image)
                .addNewPrivateRegistryCredential(privateRegistryUrl, privateRegistryCredentialsId)
                .withIdleRetentionStrategy(60)
                .build();

        Assert.assertNotNull(template);
    }


}
