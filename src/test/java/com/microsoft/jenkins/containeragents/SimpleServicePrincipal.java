package com.microsoft.jenkins.containeragents;

import java.io.Serializable;
import java.util.UUID;

import static com.microsoft.jenkins.containeragents.TestUtils.loadProperty;

public class SimpleServicePrincipal implements Serializable {
    public final String credentialsId = UUID.randomUUID().toString();

    public final String subscriptionId = loadProperty("ACS_AGENT_TEST_SUBSCRIPTION_ID");
    public final String clientId = loadProperty("ACS_AGENT_TEST_CLIENT_ID");
    public final String clientSecret = loadProperty("ACS_AGENT_TEST_CLIENT_SECRET");

    public final String tenantId = loadProperty("ACS_AGENT_TEST_TENANT_ID");

}
