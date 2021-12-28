package com.microsoft.jenkins.containeragents.builders;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.aci.AciAgent;
import com.microsoft.jenkins.containeragents.aci.AciCloud;
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate;
import com.microsoft.jenkins.containeragents.aci.AciPort;
import com.microsoft.jenkins.containeragents.aci.AciPrivateIpAddress;
import com.microsoft.jenkins.containeragents.aci.AciService;
import com.microsoft.jenkins.containeragents.aci.volumes.AzureFileVolume;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import com.microsoft.jenkins.containeragents.util.DockerRegistryUtils;
import hudson.EnvVars;
import hudson.security.ACL;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AciDeploymentTemplateBuilder {

    private static final String DEPLOY_TEMPLATE_FILENAME
            = "/com/microsoft/jenkins/containeragents/aci/deployTemplate.json";
    private static final String NETWORK_PROFILE_SNIPPET_FILENAME
            = "/com/microsoft/jenkins/containeragents/aci/networkProfileSnippet.json";
    private AciDeploymentTemplateBuilder() {
        //
    }

    @NotNull
    public static AciDeploymentTemplate buildDeploymentTemplate(AciCloud cloud, AciContainerTemplate template,
                                                                AciAgent agent) throws IOException {
        try (InputStream stream = AciService.class.getResourceAsStream(DEPLOY_TEMPLATE_FILENAME)) {
            AciPrivateIpAddress privateIpAddress = template.getPrivateIpAddress();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode tmp = mapper.readTree(stream);
            final ObjectNode parameters = mapper.createObjectNode();

            ObjectNode variables = (ObjectNode) tmp.get("variables");
            variables.put("containerName", agent.getNodeName());
            variables.put("containerImage", template.getImage());
            variables.put("osType", template.getOsType());
            variables.put("ipType", mapIpType(privateIpAddress));
            if (privateIpAddress != null) {
                variables.put("vnetName", privateIpAddress.getVnet());
                variables.put("subnetName", privateIpAddress.getSubnet());
            }
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

            addNetworkProfile(tmp, mapper, privateIpAddress);

            return new AciDeploymentTemplate(tmp, parameters);
        }
    }

    private static void addNetworkProfile(JsonNode tmp, ObjectMapper mapper, AciPrivateIpAddress privateIpAddress)
            throws IOException {
        if (privateIpAddress == null) {
            return;
        }

        ObjectNode networkProfileNode = mapper.createObjectNode();
        networkProfileNode.put("id",
                "[resourceId('Microsoft.Network/networkProfiles', variables('containerName'))]");


        ArrayNode resourcesArray = (ArrayNode) tmp.get("resources");

        ObjectNode containerGroupItem = (ObjectNode) resourcesArray.get(0);
        ObjectNode propertiesNode = (ObjectNode) containerGroupItem.get("properties");
        propertiesNode.set("networkProfile", networkProfileNode);
        ArrayNode dependencyArrayNode = mapper.createArrayNode();
        dependencyArrayNode.add("[resourceId('Microsoft.Network/networkProfiles', variables('containerName'))]");
        containerGroupItem.set("dependsOn", dependencyArrayNode);

        try (InputStream networkProfileSnippetStream =
                     AciService.class.getResourceAsStream(NETWORK_PROFILE_SNIPPET_FILENAME)) {
            JsonNode networkProfileItem = mapper.readTree(networkProfileSnippetStream);
            resourcesArray.add(networkProfileItem);
        }
    }

    private static String mapIpType(AciPrivateIpAddress privateIpAddress) {
        return privateIpAddress != null ? "Private" : "Public";
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
        logAnalyticsNode.put("logType", "ContainerInstanceLogs");
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


    public static class AciDeploymentTemplate {
        private final ObjectNode templateParameter;
        private final JsonNode deploymentTemplate;

        AciDeploymentTemplate(JsonNode deploymentTemplate, ObjectNode templateParameter) {
            this.templateParameter = templateParameter;
            this.deploymentTemplate = deploymentTemplate;
        }

        public String templateParameterAsString() {
            return templateParameter.toString();
        }

        public String deploymentTemplateAsString() {
            return deploymentTemplate.toString();
        }
    }
}
