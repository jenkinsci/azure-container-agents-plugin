/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents.volumes;

import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.Volume;

import java.io.Serializable;

public abstract class PodVolume extends AbstractDescribableImpl<PodVolume> implements Serializable {

    private static final long serialVersionUID = 8874521354L;

    public abstract String getMountPath();

    public abstract Volume buildVolume(String volumeNmae);
}
