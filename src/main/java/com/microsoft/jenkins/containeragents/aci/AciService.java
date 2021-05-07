package com.microsoft.jenkins.containeragents.aci;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.containerinstance.models.ContainerGroup;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.Deployments;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import com.microsoft.jenkins.containeragents.aci.volumes.AzureFileVolume;
import com.microsoft.jenkins.containeragents.util.DockerRegistryUtils;
import hudson.EnvVars;
import hudson.security.ACL;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;

import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AciService {
    private static final Logger LOGGER = Logger.getLogger(AciService.class.getName());

    private static final String DEPLOY_TEMPLATE_FILENAME
            = "/com/microsoft/jenkins/containeragents/aci/deployTemplate.json";

    public static void createDeployment(final AciCloud cloud,
                                        final AciContainerTemplate template,
                                        final AciAgent agent,
                                        final StopWatch stopWatch) throws Exception {
        String deployName = getDeploymentName(template);

        try (InputStream stream = AciService.class.getResourceAsStream(DEPLOY_TEMPLATE_FILENAME)) {
            final AzureResourceManager azureClient = cloud.getAzureClient();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode tmp = mapper.readTree(stream);
            final ObjectNode parameters = mapper.createObjectNode();

            ObjectNode variables = (ObjectNode) tmp.get("variables");
            variables.put("containerName", agent.getNodeName());
            variables.put("containerImage", template.getImage());
            variables.put("osType", template.getOsType());
            variables.put("cpu", template.getCpu());
            variables.put("memory", template.getMemory());
            variables.put("jenkinsInstance",
                    Jenkins.get().getLegacyInstanceId());

            addLogAnalytics(tmp, parameters, mapper, cloud);
            addCommandNode(tmp, template.getCommand(), agent);

            for (AciPort port : template.getPorts()) {
                if (StringUtils.isBlank(port.getPort())) {
                    continue;
                }
                addPortNode(tmp, mapper, port.getPort());
            }
            if (template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_SSH)) {
                addPortNode(tmp, mapper, String.valueOf(template.getSshPort()));
            }

            addEnvNode(tmp, mapper, template.getEnvVars());

            for (DockerRegistryEndpoint registryEndpoint : template.getPrivateRegistryCredentials()) {
                addImageRegistryCredentialNode(tmp, mapper, registryEndpoint);
            }

            for (AzureFileVolume volume : template.getVolumes()) {
                if (StringUtils.isBlank(volume.getMountPath())
                        || StringUtils.isBlank(volume.getShareName())
                        || StringUtils.isBlank(volume.getCredentialsId())) {
                    continue;
                }
                addAzureFileVolumeNode(tmp, mapper, volume);
            }

            // register the deployment for cleanup
            AciCleanTask.DeploymentRegistrar deploymentRegistrar = AciCleanTask.DeploymentRegistrar.getInstance();
            deploymentRegistrar.registerDeployment(cloud.getName(), cloud.getResourceGroup(), deployName);

            azureClient.deployments()
                    .define(deployName)
                    .withExistingResourceGroup(cloud.getResourceGroup())
                    .withTemplate(tmp.toString())
                    .withParameters(parameters.toString())
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
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    private static void addPortNode(JsonNode tmp, ObjectMapper mapper, String port) {
        JsonNode propertiesNode = tmp.get("resources").get(0).get("properties");
        ArrayNode containerPortsNodes = (ArrayNode) propertiesNode.get("containers")
                .get(0).get("properties").get("ports");
        ArrayNode ipPortsNodes = (ArrayNode) propertiesNode.get("ipAddress").get("ports");

        ObjectNode newContainerPortNode = mapper.createObjectNode();
        newContainerPortNode.put("port", port);
        containerPortsNodes.add(newContainerPortNode);

        ObjectNode newIpPortNode = mapper.createObjectNode();
        newIpPortNode.put("protocol", "tcp");
        newIpPortNode.put("port", port);
        ipPortsNodes.add(newIpPortNode);
    }

    private static void addCommandNode(JsonNode tmp, String[] commands) {
        ArrayNode commandNode = (ArrayNode) tmp.get("resources").get(0)
                .get("properties").get("containers").get(0)
                .get("properties").get("command");

        for (String command : commands) {
            commandNode.add(command);
        }
    }

    private static void addCommandNode(JsonNode tmp, String command, AciAgent agent) {
        if (StringUtils.isBlank(command)) {
            return;
        }
        String replaceCommand = commandReplace(command, agent);
        addCommandNode(tmp, StringUtils.split(replaceCommand, ' '));
    }

    private static void addLogAnalytics(JsonNode tmp, ObjectNode parameters,
            ObjectMapper mapper, AciCloud aciCloud) {

        if (StringUtils.isBlank(aciCloud.getLogAnalyticsCredentialsId())) {
            return;
        }

        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.withId(aciCloud.getLogAnalyticsCredentialsId()));
        if (credentials == null) {
            return;
        }

        defineParameter(tmp, "workspaceKey", "secureString", mapper);
        putParameter(parameters, "workspaceKey", credentials.getPassword().getPlainText(), mapper);

        ObjectNode diagnosticsNode = mapper.createObjectNode();
        ObjectNode logAnalyticsNode = mapper.createObjectNode();
        logAnalyticsNode.put("workspaceId", credentials.getUsername());
        logAnalyticsNode.put("logType", "ContainerInsights");
        logAnalyticsNode.put("workspaceKey", "[parameters('workspaceKey')]");
        diagnosticsNode.set("logAnalytics", logAnalyticsNode);
        ((ObjectNode) tmp.get("resources").get(0).get("properties"))
                .set("diagnostics", diagnosticsNode);
    }

     private static void putParameter(ObjectNode template, String name, String value, ObjectMapper mapper) {
        ObjectNode objectNode = mapper.createObjectNode();
        objectNode.put("value", value);

        template.set(name, objectNode);
    }

    private static void defineParameter(JsonNode template, String name, String value, ObjectMapper mapper) {
        ObjectNode objectNode = mapper.createObjectNode();
        objectNode.put("type", value);

        ((ObjectNode) template.get("parameters")).set(name, objectNode);
    }

    private static void addImageRegistryCredentialNode(JsonNode tmp,
                                                       ObjectMapper mapper,
                                                       DockerRegistryEndpoint endpoint) {
        if (StringUtils.isBlank(endpoint.getCredentialsId())) {
            return;
        }
        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.withId(endpoint.getCredentialsId()));
        if (credentials == null) {
            return;
        }
        ArrayNode credentialNode = (ArrayNode) tmp.get("resources").get(0)
                .get("properties").get("imageRegistryCredentials");
        ObjectNode newCredentialNode = mapper.createObjectNode();
        newCredentialNode.put("server", StringUtils.isBlank(endpoint.getUrl())
                ? "index.docker.io"
                : DockerRegistryUtils.formatUrlToWithoutProtocol(endpoint.getUrl()));
        newCredentialNode.put("username", credentials.getUsername());
        newCredentialNode.put("password", credentials.getPassword().getPlainText());

        credentialNode.add(newCredentialNode);
    }

    private static void addEnvNode(JsonNode tmp, ObjectMapper mapper, List<PodEnvVar> envVars) {
        ArrayNode envVarNode = (ArrayNode) tmp.get("resources").get(0)
                .get("properties").get("containers").get(0).get("properties").get("environmentVariables");

        for (PodEnvVar envVar : envVars) {
            if (StringUtils.isBlank(envVar.getKey())) {
                continue;
            }
            ObjectNode newCredentialNode = mapper.createObjectNode();
            newCredentialNode.put("name", envVar.getKey());
            newCredentialNode.put("value", envVar.getValue());
            envVarNode.add(newCredentialNode);
        }
    }

    private static void addAzureFileVolumeNode(JsonNode tmp, ObjectMapper mapper, AzureFileVolume volume) {
        ArrayNode volumeMountsNode = (ArrayNode) tmp.get("resources").get(0)
                .get("properties").get("containers").get(0).get("properties").get("volumeMounts");
        ArrayNode volumesNode = (ArrayNode) tmp.get("resources").get(0).get("properties").get("volumes");

        ObjectNode newVolumeMountsNode = mapper.createObjectNode();
        String volumeName = AzureContainerUtils.generateName("volume", Constants.ACI_VOLUME_NAME_LENGTH);
        newVolumeMountsNode.put("name", volumeName);
        newVolumeMountsNode.put("mountPath", volume.getMountPath());

        volumeMountsNode.add(newVolumeMountsNode);

        ObjectNode newAzureFileNode = mapper.createObjectNode();
        newAzureFileNode.put("shareName", volume.getShareName());
        newAzureFileNode.put("storageAccountName", volume.getStorageAccountName());
        newAzureFileNode.put("storageAccountKey", volume.getStorageAccountKey());

        ObjectNode newVolumesNode = mapper.createObjectNode();
        newVolumesNode.put("name", volumeName);
        newVolumesNode.set("azureFile", newAzureFileNode);

        volumesNode.add(newVolumesNode);
    }

    private static String commandReplace(String command, AciAgent agent) {
        String serverUrl = Jenkins.get().getRootUrl();
        String nodeName = agent.getNodeName();

        SlaveComputer computer = agent.getComputer();
        if (computer == null) {
            throw new IllegalStateException("Agent must be online at this point");
        }

        String secret = computer.getJnlpMac();

        Map<String, String> argumentsToExpand = buildCommand(command, serverUrl, nodeName, secret);
        EnvVars arguments = new EnvVars(argumentsToExpand);
        return arguments.expand(command);
    }

    private static Map<String, String> buildCommand(String command, String serverUrl, String nodeName, String secret) {
        Map<String, String> arguments = new HashMap<>();

        if (command.contains("${rootUrl}")) {
            arguments.put("rootUrl", serverUrl);
        }

        if (command.contains("${nodeName}")) {
            arguments.put("nodeName", nodeName);
        }

        if (command.contains("${secret}")) {
            arguments.put("secret", secret);
        }

        if (command.contains("${instanceIdentity}")) {
            String instanceIdentity = Base64.getEncoder()
                    .encodeToString(InstanceIdentity.get().getPublic().getEncoded());
            arguments.put("instanceIdentity", instanceIdentity);
        }

        return arguments;
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

    private AciService() {
        //
    }
}
