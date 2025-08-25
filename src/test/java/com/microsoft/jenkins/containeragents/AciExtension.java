package com.microsoft.jenkins.containeragents;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountKey;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.ShareServiceClientBuilder;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import hudson.util.Secret;
import io.jenkins.plugins.azuresdk.HttpClientRetriever;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.Serializable;
import java.util.UUID;
import java.util.logging.Logger;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AciExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = Logger.getLogger(AciExtension.class.getName());

    public final AciData data = new AciData();

    public AzureResourceManager azureClient = null;

    public static AzureResourceManager getAzureClient(
            AzureCredentials azureCredentials) {
        AzureProfile profile = new AzureProfile(azureCredentials.getAzureEnvironment());
        TokenCredential tokenCredential = AzureCredentials.getTokenCredential(azureCredentials);

        return AzureResourceManager
                .configure()
                .withHttpClient(HttpClientRetriever.get())
                .authenticate(tokenCredential, profile)
                .withSubscription(azureCredentials.getSubscriptionId());
    }

    public static class AciData implements Serializable {
        public final String label = AzureContainerUtils.generateName("AciTemplateTest",3);
        public String storageAccountCredentialsId;
        public String fileShareName;
        public AzureStorageAccount.StorageAccountCredential storageAccountCredential;

        public final SimpleServicePrincipal servicePrincipal = new SimpleServicePrincipal();

        public final String cloudName = AzureContainerUtils.generateName("AzureContainerTest", 5);
        public final String location = loadProperty("ACI_AGENT_TEST_AZURE_LOCATION", "East US");
        public final String resourceGroup = AzureContainerUtils.generateName(loadProperty("ACI_AGENT_TEST_RESOURCE_GROUP", "AzureContainerTest"), 3);

        public String image;
        public String privateRegistryUrl;
        public String privateRegistryCredentialsId;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        prepareServicePrincipal();
        prepareResourceGroup();
        prepareStorageAccount();
        prepareImage("ACI_AGENT_TEST_IMAGE",
                "ACI_AGENT_TEST_REGISTRY_URL",
                "ACI_AGENT_TEST_REGISTRY_NAME",
                "ACI_AGENT_TEST_REGISTRY_KEY");
    }

    protected void prepareServicePrincipal() {
        AzureCredentials servicePrincipalCredentials = getServicePrincipalCredentials();
        this.azureClient = getAzureClient(servicePrincipalCredentials);
    }

    public void prepareImage(String imageEnv, String privateRegistryUrlEnv, String privateRegistryNameEnv, String privateRegistryKeyEnv) throws Exception {
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
                Secret.fromString(data.servicePrincipal.clientSecret)
        );
        azureCredentials.setTenant(data.servicePrincipal.tenantId);
        return azureCredentials;
    }


    @Override
    public void afterEach(ExtensionContext context) {
        cleanResourceGroup();
    }

    public void prepareResourceGroup() {
        String resourceGroup = data.resourceGroup;
        try {
            azureClient.resourceGroups().getByName(resourceGroup);
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                LOGGER.info("Creating resource group: " + resourceGroup);
                azureClient.resourceGroups().define(resourceGroup).withRegion(data.location).create();
                LOGGER.info("Created resource group: " + resourceGroup);
            }
        }
    }

    public void prepareStorageAccount() {
        String accountName;
        StorageAccountKey accountKey;

        String randomString = RandomStringUtils.secure().next(8, "abcdfghjklmnpqrstvwxz0123456789");
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

        ShareServiceClient shareServiceClient = new ShareServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
        String theFileShareName = RandomStringUtils.secure().next(8, "abcdfghjklmnpqrstvwxz0123456789");
        data.fileShareName = theFileShareName;
        ShareClient fileShare = shareServiceClient.getShareClient((theFileShareName));
        LOGGER.info("Creating file share: " + theFileShareName);
        if (!fileShare.exists()) {
            fileShare.create();
        }
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
                "",
                "").getStorageCred();
    }

    public void cleanResourceGroup() {
        String resourceGroup = data.resourceGroup;
        LOGGER.info("Deleting resource group: " + resourceGroup);
        azureClient.resourceGroups().deleteByName(resourceGroup);
        LOGGER.info("Deleted resource group: " + resourceGroup);

        ManagementException managementException = assertThrows(ManagementException.class,
                () -> azureClient.resourceGroups().getByName(resourceGroup));

        assertThat(managementException.getResponse().getStatusCode(), is(404));
    }
}
