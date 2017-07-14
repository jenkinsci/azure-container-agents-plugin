package com.microsoft.azure.containeragents;

import hudson.slaves.AbstractCloudComputer;

public class KubernetesComputer extends AbstractCloudComputer<KubernetesAgent> {

    public KubernetesComputer(KubernetesAgent slave) {
        super(slave);
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

}