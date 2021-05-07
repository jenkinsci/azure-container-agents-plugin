package com.microsoft.jenkins.containeragents;


import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.microsoft.azure.util.AzureCredentials;
import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciService;
import com.microsoft.jenkins.containeragents.builders.AciCloudBuilder;
import com.microsoft.jenkins.containeragents.builders.AciContainerTemplateBuilder;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoftopentechnologies.windowsazurestorage.helper.AzureStorageAccount;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import hudson.util.Secret;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AciCloudIT {

    @Rule
    public AciRule aciRule = new AciRule();

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void testProvisionInboundAgent() throws Throwable {
        rr.then(new InboundAgentOK(aciRule.data));
    }

    private static class InboundAgentOK implements RealJenkinsRule.Step {

        private final AciRule.AciData aciRuleData;
        private static final Logger LOGGER = Logger.getLogger(InboundAgentOK.class.getName());
        private AzureCredentials azureCredentials;

        public InboundAgentOK(AciRule.AciData aciRuleData) {
            this.aciRuleData = aciRuleData;
        }

        @Override
        public void run(JenkinsRule r) throws Throwable {
            setup(r);

            List<NodeProvisioner.PlannedNode> plannedNodes = (List<NodeProvisioner.PlannedNode>) r.jenkins.clouds.get(0).provision(new Cloud.CloudState(new LabelAtom(aciRuleData.label), 0), 1);
            Node node = plannedNodes.get(0).future.get();
            assertTrue(node instanceof AciAgent);
            AciAgent agent = (AciAgent) node;

            //Test running job on slave
            FreeStyleProject project = r.createFreeStyleProject(AzureContainerUtils.generateName("AciTestJob", 5));
            project.setAssignedLabel(new LabelAtom(aciRuleData.label));
            project.getBuildersList().add(new Shell("cd /afs"));
            Future<FreeStyleBuild> build = project.scheduleBuild2(0);
            r.assertBuildStatus(Result.SUCCESS, build.get(120, TimeUnit.SECONDS));

            //Test deleting remote agent and deployment
            AciService.deleteAciContainerGroup(aciRuleData.servicePrincipal.credentialsId, aciRuleData.resourceGroup, agent.getNodeName(), agent.getDeployName());
            assertNull(AciRule.getAzureClient(azureCredentials).containerGroups().getByResourceGroup(aciRuleData.resourceGroup, agent.getNodeName()));

            //Test deleting Jenkins node
            agent.terminate();
            assertNull(r.jenkins.getNode(agent.getNodeName()));
        }

        private void setup(JenkinsRule r) throws Exception {
            List<Credentials> credentials = SystemCredentialsProvider.getInstance().getDomainCredentialsMap().get(Domain.global());
            azureCredentials = new AzureCredentials(
                    CredentialsScope.GLOBAL,
                    aciRuleData.servicePrincipal.credentialsId,
                    "Azure Credentials for Azure Container Agent Test",
                    aciRuleData.servicePrincipal.subscriptionId,
                    aciRuleData.servicePrincipal.clientId,
                    Secret.fromString(aciRuleData.servicePrincipal.clientSecret)
            );
            azureCredentials.setTenant(aciRuleData.servicePrincipal.tenantId);
            credentials.add(azureCredentials);
            LOGGER.info("AccountName: " + aciRuleData.storageAccountCredential.getStorageAccountName() + " Key: " + aciRuleData.storageAccountCredential.getStorageAccountKey());
            credentials.add(new AzureStorageAccount(CredentialsScope.GLOBAL,
                    aciRuleData.storageAccountCredentialsId,
                    "Storage Credential for Test",
                    aciRuleData.storageAccountCredential.getStorageAccountName(),
                    aciRuleData.storageAccountCredential.getStorageAccountKey(),
                    ""));
            r.jenkins.setSlaveAgentPort(Integer.parseInt(TestUtils.loadProperty("ACI_INBOUND_AGENT_PORT")));

            AciContainerTemplate aciContainerTemplate = prepareTemplate();
            prepareCloud(r, aciContainerTemplate);
        }

        public AciContainerTemplate prepareTemplate() {
            String inboundUrl = loadProperty("ACI_JENKINS_AGENT_INBOUND_HOST");

            return new AciContainerTemplateBuilder()
                    .withName(AzureContainerUtils.generateName("AciTemplate", 5))
                    .withLabel(aciRuleData.label)
                    .addNewEnvVar("ENV", "echo pass")
                    .addNewPort("8080")
                    .addNewAzureFileVolume("/afs", aciRuleData.fileShareName, aciRuleData.storageAccountCredentialsId)
                    .withImage(aciRuleData.image)
                    .addNewPrivateRegistryCredential(aciRuleData.privateRegistryUrl, aciRuleData.privateRegistryCredentialsId)
                    .withIdleRetentionStrategy(60)
                    .withCommand(String.format("jenkins-agent -direct %s -instanceIdentity ${instanceIdentity} ${secret} ${nodeName}", inboundUrl))
                    .build();
        }

        public void prepareCloud(JenkinsRule r, AciContainerTemplate template) {
            AciCloud aciCloud = new AciCloudBuilder().withCloudName(aciRuleData.cloudName)
                    .withAzureCredentialsId(aciRuleData.servicePrincipal.credentialsId)
                    .withResourceGroup(aciRuleData.resourceGroup)
                    .addToTemplates(template)
                    .build();

            r.jenkins.clouds.add(aciCloud);

        }
    }
}
