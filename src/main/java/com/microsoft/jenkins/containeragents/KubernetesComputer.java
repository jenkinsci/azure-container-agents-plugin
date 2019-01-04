/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

import hudson.slaves.AbstractCloudComputer;

import javax.annotation.Nullable;

public class KubernetesComputer extends AbstractCloudComputer<KubernetesAgent> implements TrackedItem {

    private final ProvisioningActivity.Id provisioningId;

    public KubernetesComputer(KubernetesAgent slave) {
        super(slave);

        this.provisioningId = slave.getId();
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }
}