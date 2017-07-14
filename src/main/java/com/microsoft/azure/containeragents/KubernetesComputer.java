package com.microsoft.azure.containeragents;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;

import java.util.logging.Level;
import java.util.logging.Logger;


public class KubernetesComputer extends AbstractCloudComputer<KubernetesAgent> {
    private static final Logger LOGGER = Logger.getLogger(KubernetesComputer.class.getName());

    public KubernetesComputer(KubernetesAgent slave) {
        super(slave);
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

}