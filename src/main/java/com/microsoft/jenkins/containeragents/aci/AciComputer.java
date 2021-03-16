package com.microsoft.jenkins.containeragents.aci;

import hudson.slaves.AbstractCloudComputer;

public class AciComputer extends AbstractCloudComputer<AciAgent> {
    public AciComputer(AciAgent agent) {
        super(agent);
    }

    @Override
    public String toString() {
        return String.format("AciComputer name: %s agent: %s", getName(), getNode());
    }
}
