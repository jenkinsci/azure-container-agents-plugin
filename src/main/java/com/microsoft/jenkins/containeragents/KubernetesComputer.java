/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents;

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
