package com.microsoft.jenkins.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.jenkins.containeragents.builders.KubernetesCloudBuilder;
import com.microsoft.jenkins.containeragents.builders.PodTemplateBuilder;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;

public class KubernetesRule extends AzureContainerRule {

    public final String TYPE;
    public static final String AKS = "AKS";
    public static final String K8S = "K8S";

    public String serviceName;
    public String acsCredentialsId = null;
    public String namespace = "default";

    public KubernetesCloud cloud = null;
    public PodTemplate template = null;

    public KubernetesClient kubernetesClient = null;

    public KubernetesRule(String type) {
        super();
        TYPE = type;
        location = loadProperty(TYPE+"_AGENT_TEST_AZURE_LOCATION", "East US");
        resourceGroup = loadProperty(TYPE+"_AGENT_TEST_RESOURCE_GROUP");
    }

    @Override
    public void before() throws Exception {
        super.before();
        serviceName = TestUtils.loadProperty(TYPE+"_AGENT_TEST_SERVICE_NAME");
        if (TYPE.equals(KubernetesRule.K8S)) {
            prepareAcsCredential();
        }
        prepareImage(TYPE+"_AGENT_TEST_IMAGE",
                TYPE+"_AGENT_TEST_REGISTRY_URL",
                TYPE+"_AGENT_TEST_REGISTRY_NAME",
                TYPE+"_AGENT_TEST_REGISTRY_KEY");
        prepareTemplate();
        prepareCloud();

        kubernetesClient = KubernetesService.getKubernetesClient(
                credentialsId,
                resourceGroup,
                serviceName,
                namespace,
                acsCredentialsId
        );
    }

    public void prepareAcsCredential() throws Exception {
        String username = TestUtils.loadProperty(TYPE+"_AGENT_TEST_SSH_USERNAME");
        String privateKey = FileUtils.readFileToString(new File(TestUtils.loadProperty(TYPE+"_AGENT_TEST_SSH_PRIVATE_KEY_PATH")));
        String passphrase = TestUtils.loadProperty(TYPE+"_AGENT_TEST_SSH_PASSPHRASE");

        if (StringUtils.isBlank(username) || StringUtils.isBlank(privateKey)) {
            return;
        }

        BasicSSHUserPrivateKey acsCredentials = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                acsCredentialsId = UUID.randomUUID().toString(),
                username,
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
                passphrase,
                "Acs Credentials for Test"
        );
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global()).add(acsCredentials);

    }

    public void prepareTemplate() throws Exception {
        template = new PodTemplateBuilder()
                .withName(AzureContainerUtils.generateName("K8sTemplate", 5))
                .withLabel(TYPE+"_TemplateTest")
                .withImage(image)
                .addNewPrivateRegistryCredential(privateRegistryUrl, privateRegistryCredentialsId)
                .addNewEnvVar("ENV", "echo pass")
                .withIdleRetentionStrategy(60)
                .build();
    }

    public void prepareCloud() {
        cloud = new KubernetesCloudBuilder()
                .withCloudName(cloudName)
                .withResourceGroup(resourceGroup)
                .withServiceName(serviceName)
                .withAzureCredentialsId(credentialsId)
                .withAcsCredentialsId(acsCredentialsId)
                .addToTemplates(template)
                .build();
    }


}
