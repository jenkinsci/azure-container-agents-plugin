package com.microsoft.jenkins.containeragents;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.storage.StorageAccount;
import com.microsoft.azure.management.storage.StorageAccountKey;
import com.microsoft.azure.storage.file.CloudFileClient;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.builders.AciCloudBuilder;
import com.microsoft.jenkins.containeragents.builders.AciContainerTemplateBuilder;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.TokenCache;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;

public class AciRule extends AzureContainerRule {

    public AciContainerTemplate template = null;
    public AciCloud cloud = null;

    public AciRule() {
        super();
    }

    @Override
    public void before() throws Exception {
        super.before();
        prepareTemplate();
        prepareCloud();
    }

    public void prepareStorageAccount() throws Exception {
        Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();
        StorageAccount storageAccount = azureClient.storageAccounts()
                .define(RandomStringUtils.random(8, "abcdfghjklmnpqrstvwxz0123456789"))
                .withRegion("East US")
                .withExistingResourceGroup(resourceGroup)
                .create();

        StorageAccountKey storageAccountKey = storageAccount.getKeys().get(0);



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
                .addNewEnvVar("key", "value")
                .addNewPort("8080")
                .withIdleRetentionStrategy(60)
                .build();

        Assert.assertNotNull(template);
    }
}
