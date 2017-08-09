package com.microsoft.azure.containeragents.aci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.containeragents.util.AzureContainerUtils;
import com.microsoft.azure.containeragents.util.Constants;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.DeploymentMode;
import com.microsoft.azure.util.AzureCredentials;
import hudson.EnvVars;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Level;
import java.util.logging.Logger;


public final class AciService {
    private static final Logger LOGGER = Logger.getLogger(AciService.class.getName());

    private static final String DEPLOY_TEMPLATE_FILENAME
            = "/com/microsoft/azure/containeragents/aci/deployTemplate.json";

    public static void createDeployment(final AciCloud cloud,
                                        final AciContainerTemplate template,
                                        final AciAgent agent) throws Exception {
        try {
            AzureCredentials.ServicePrincipal servicePrincipal
                    = AzureCredentials.getServicePrincipal(cloud.getCredentialsId());
            final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode tmp = mapper.readTree(AciService.class.getResourceAsStream(DEPLOY_TEMPLATE_FILENAME));

            ObjectNode.class.cast(tmp.get("variables")).put("containerName", agent.getNodeName());
            ObjectNode.class.cast(tmp.get("variables")).put("containerImage", template.getImage());
            ObjectNode.class.cast(tmp.get("variables")).put("osType", template.getOsType());
            ObjectNode.class.cast(tmp.get("variables")).put("cpu", template.getCpu());
            ObjectNode.class.cast(tmp.get("variables")).put("memory", template.getMemory());

            addCommandNode(tmp, mapper, template.getCommand(), agent);

            for (AciPort port : template.getPorts()) {
                addPortNode(tmp, mapper, port.getPort());
            }
            String deployName = getDeploymentName(template);

            azureClient.deployments()
                    .define(deployName)
                    .withExistingResourceGroup(cloud.getResourceGroup())
                    .withTemplate(tmp.toString())
                    .withParameters("{}")
                    .withMode(DeploymentMode.INCREMENTAL)
                    .beginCreate();

            final int retryInterval = 20 * 1000;

            LOGGER.log(Level.INFO, "Waiting for deployment {0}", deployName);
            while (true) {
                Deployment deployment
                        = azureClient.deployments().getByResourceGroup(cloud.getResourceGroup(), deployName);

                if (deployment.provisioningState().equalsIgnoreCase("succeeded")) {
                    break;
                } else if (deployment.provisioningState().equalsIgnoreCase("Failed")) {
                    throw new Exception("Deployment status: Failed");
                } else {
                    Thread.sleep(retryInterval);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to deploy: {0}", e);
            throw new Exception(e);
        }
    }

    private static void addPortNode(JsonNode tmp, ObjectMapper mapper, String port) {
        JsonNode propertiesNode = tmp.get("resources").get(0).get("properties");
        ArrayNode containerPortsNodes = ArrayNode.class.cast(propertiesNode.get("containers")
                .get(0).get("properties").get("ports"));
        ArrayNode ipPortsNodes = ArrayNode.class.cast(propertiesNode.get("ipAddress").get("ports"));

        ObjectNode newContainerPortNode = mapper.createObjectNode();
        newContainerPortNode.put("port", port);
        containerPortsNodes.add(newContainerPortNode);

        ObjectNode newIpPortNode = mapper.createObjectNode();
        newIpPortNode.put("protocol", "tcp");
        newIpPortNode.put("port", port);
        ipPortsNodes.add(newIpPortNode);
    }

    private static void addCommandNode(JsonNode tmp, ObjectMapper mapper, String[] commands) {
        ArrayNode commandNode = ArrayNode.class.cast(tmp.get("resources").get(0)
                .get("properties").get("containers").get(0)
                .get("properties").get("command"));

        for (int i = 0; i < commands.length; i++) {
            commandNode.add(commands[i]);
        }
    }

    private static void addCommandNode(JsonNode tmp, ObjectMapper mapper, String command, AciAgent agent) {
        if (StringUtils.isBlank(command)) {
            return;
        }
        String replaceCommand = commandReplace(command, agent);
        addCommandNode(tmp, mapper, StringUtils.split(replaceCommand, ' '));
    }

    private static String commandReplace(String command, AciAgent agent) {
        String serverUrl = Jenkins.getInstance().getRootUrl();
        String nodeName = agent.getNodeName();
        String secret = agent.getComputer().getJnlpMac();
        EnvVars arguments = new EnvVars("rootUrl", serverUrl, "nodeName", nodeName, "secret", secret);
        return arguments.expand(command);
    }

    private static String getDeploymentName(AciContainerTemplate template) {
        return AzureContainerUtils.generateName(template.getName(), Constants.ACI_DEPLOYMENT_RANDOM_NAME_LENGTH);
    }

    public static void deleteAciContainerGroup(String credentialsId, String resourceGroup, String containerGroupName) {
        AzureCredentials.ServicePrincipal servicePrincipal
                = AzureCredentials.getServicePrincipal(credentialsId);
        final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

        try {
            azureClient.genericResources().delete(resourceGroup,
                    "Microsoft.ContainerInstance",
                    "",
                    "containerGroups",
                    containerGroupName,
                    "2017-08-01-preview");
            LOGGER.log(Level.INFO, "Delete ACI Container Group: {0} successfully", containerGroupName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Delete ACI Container Group: {0} failed: {1}",
                    new Object[] {containerGroupName, e});
        }
    }

    private AciService() {
        //
    }
}
