package com.microsoft.azure.containeragents.aci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureCredentials;

import java.io.IOException;


public final class AciService {

    private static final String DEPLOY_TEMPLATE_FILENAME
            = "./com/microsoft/azure/containeragents/aci/deployTemplate.json";

    public static void createDeployment(final AciCloud cloud, final AciContainer template) throws IOException {
        AzureCredentials.ServicePrincipal servicePrincipal
                = AzureCredentials.getServicePrincipal(cloud.getCredentialsId());
        final Azure azureClient = TokenCache.getInstance(servicePrincipal).getAzureClient();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode tmp = mapper.readTree(AciService.class.getResourceAsStream(DEPLOY_TEMPLATE_FILENAME));

        ObjectNode.class.cast(tmp.get("variables")).put("containerName", template.getName());
        ObjectNode.class.cast(tmp.get("variables")).put("containerImage", template.getImage());
        ObjectNode.class.cast(tmp.get("variables")).put("osType", template.getOsType());
        ObjectNode.class.cast(tmp.get("variables")).put("command", template.getCommand());
        ObjectNode.class.cast(tmp.get("variables")).put("cpu", template.getCpu());
        ObjectNode.class.cast(tmp.get("variables")).put("memory", template.getMemory());

        for (AciPort port : template.getPorts()) {
            addPortNode(tmp, mapper, port.getPort());
        }
    }

    private static void addPortNode(JsonNode tmp, ObjectMapper mapper, String port) {
        
    }

    private AciService() {
        //
    }
}
