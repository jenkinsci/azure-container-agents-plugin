package com.microsoft.jenkins.containeragents.aci;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.containerinstance.models.ContainerGroup;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.Deployments;
import com.microsoft.jenkins.containeragents.builders.AciDeploymentTemplateBuilder;
import com.microsoft.jenkins.containeragents.builders.AciDeploymentTemplateBuilder.AciDeploymentTemplate;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import org.apache.commons.lang3.time.StopWatch;

import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AciService {
    private static final Logger LOGGER = Logger.getLogger(AciService.class.getName());

    private AciService() {
        //
    }

    public static void createDeployment(final AciCloud cloud,
                                        final AciContainerTemplate template,
                                        final AciAgent agent,
                                        final StopWatch stopWatch) throws Exception {
        String deployName = getDeploymentName(template);
        AciDeploymentTemplate deploymentTemplate =
                new AciDeploymentTemplateBuilder().buildDeploymentTemplate(cloud, template, agent);

        // register the deployment for cleanup
        AciCleanTask.DeploymentRegistrar deploymentRegistrar = AciCleanTask.DeploymentRegistrar.getInstance();
        deploymentRegistrar.registerDeployment(cloud.getName(), cloud.getResourceGroup(), deployName);

        String armTemplateForDeployment = deploymentTemplate.deploymentTemplateAsString();
        LOGGER.log(Level.FINE, "Deployment ARM Template: {0}", armTemplateForDeployment);
        String armTemplateParameterForDeployment = deploymentTemplate.templateParameterAsString();
        LOGGER.log(Level.FINE, "Deployment ARM Template Parameter: {0}", armTemplateParameterForDeployment);
        final AzureResourceManager azureClient = cloud.getAzureClient();
        azureClient.deployments()
                .define(deployName)
                .withExistingResourceGroup(cloud.getResourceGroup())
                .withTemplate(armTemplateForDeployment)
                .withParameters(armTemplateParameterForDeployment)
                .withMode(DeploymentMode.INCREMENTAL)
                .beginCreate();

        //register deployName
        agent.setDeployName(deployName);

        //Wait deployment to success

        final int retryInterval = 10 * 1000;

        LOGGER.log(Level.INFO, "Waiting for deployment {0}", deployName);
        while (true) {
            if (AzureContainerUtils.isTimeout(template.getTimeout(), stopWatch.getTime())) {
                throw new TimeoutException("Deployment timeout");
            }
            Deployment deployment
                    = azureClient.deployments().getByResourceGroup(cloud.getResourceGroup(), deployName);

            if (deployment.provisioningState().equalsIgnoreCase("succeeded")) {
                LOGGER.log(Level.INFO, "Deployment {0} succeed", deployName);
                break;
            } else if (deployment.provisioningState().equalsIgnoreCase("Failed")) {
                throw new Exception(String.format("Deployment %s status: Failed", deployName));
            } else {
                // If half of time passed, we need to inspect what happened from logs
                if (AzureContainerUtils.isHalfTimePassed(template.getTimeout(), stopWatch.getTime())) {
                    ContainerGroup containerGroup
                            = azureClient.containerGroups()
                            .getByResourceGroup(cloud.getResourceGroup(), agent.getNodeName());
                    if (containerGroup != null) {
                        LOGGER.log(Level.INFO, "Logs from container {0}: {1}",
                                new Object[]{agent.getNodeName(),
                                        containerGroup.getLogContent(agent.getNodeName())});
                    }
                }
                Thread.sleep(retryInterval);
            }
        }
    }

    private static String getDeploymentName(AciContainerTemplate template) {
        return AzureContainerUtils.generateName(template.getName(), Constants.ACI_DEPLOYMENT_RANDOM_NAME_LENGTH);
    }

    public static void deleteAciContainerGroup(String credentialsId,
                                               String resourceGroup,
                                               String containerGroupName,
                                               String deployName) {
        AzureResourceManager azureClient;

        try {
            azureClient = AzureContainerUtils.getAzureClient(credentialsId);
            azureClient.containerGroups().deleteByResourceGroup(resourceGroup, containerGroupName);
            LOGGER.log(Level.INFO, "Delete ACI Container Group: {0} successfully", containerGroupName);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format("Delete ACI Container Group: %s failed", containerGroupName), e);
            return;
        }

        try {
            //To avoid to many deployments. May over deployment limits.
            if (deployName != null) {
                // Only to delete succeeded deployments for future debugging.
                Deployments deployments = azureClient.deployments();
                Deployment deployment = deployments.getByResourceGroup(resourceGroup, deployName);
                if (deployment != null) {
                    String provisioningState = deployment.provisioningState();
                    LOGGER.fine(() -> String.format("Checking deployment: %s, provisioning state: %s",
                            deployName, provisioningState));
                    if (provisioningState
                            .equalsIgnoreCase("succeeded")) {
                        deployments.deleteByResourceGroup(resourceGroup, deployName);
                        LOGGER.log(Level.INFO, "Delete ACI deployment: {0} successfully", deployName);
                    }
                } else {
                    LOGGER.fine(() -> String.format("Skipped deployment: %s as we couldn't find it", deployName));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format("Delete ACI deployment: %s failed", deployName), e);
        }
    }

}
