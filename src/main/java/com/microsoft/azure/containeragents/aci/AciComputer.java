package com.microsoft.azure.containeragents.aci;

import hudson.slaves.AbstractCloudComputer;

public class AciComputer extends AbstractCloudComputer<AciAgent> {
    public AciComputer(AciAgent agent) {
        super(agent);
    }

    @Override
    public String toString() {
        return String.format("AciComputer name: %s slave: %s", getName(), getNode());
    }
}
