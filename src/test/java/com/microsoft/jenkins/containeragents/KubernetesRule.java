package com.microsoft.jenkins.containeragents;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.microsoft.jenkins.containeragents.builders.KubernetesCloudBuilder;
import com.microsoft.jenkins.containeragents.builders.PodTemplateBuilder;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang.StringUtils;

import java.util.UUID;

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
    }

    @Override
    public void before() throws Exception {
        super.before();
        serviceName = TestUtils.loadProperty(TYPE+"_AGENT_TEST_SERVICE_NAME");
        prepareAcsCredential();
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
        String username = TestUtils.loadProperty(TYPE+"_AGENT_SSH_USERNAME");
        String privateKey = TestUtils.loadProperty(TYPE+"_AGENT_SSH_PRIVATE_KEY");
        String passphrase = TestUtils.loadProperty(TYPE+"_AGENT_SSH_PASSPHRASE");

        if (StringUtils.isBlank(username) || StringUtils.isBlank(privateKey)) {
            return;
        }

        new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                acsCredentialsId = UUID.randomUUID().toString(),
                username,
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
                passphrase,
                "Acs Credentials for Test"
        );
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
