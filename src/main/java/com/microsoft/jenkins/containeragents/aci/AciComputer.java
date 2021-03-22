package com.microsoft.jenkins.containeragents.aci;

import hudson.slaves.AbstractCloudComputer;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

public class AciComputer extends AbstractCloudComputer<AciAgent> implements TrackedItem {

    private final ProvisioningActivity.Id provisioningId;

    public AciComputer(AciAgent agent) {
        super(agent);
        this.provisioningId = agent.getId();
    }

    @Override
    public String toString() {
        return String.format("AciComputer name: %s agent: %s", getName(), getNode());
    }

    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }
}
