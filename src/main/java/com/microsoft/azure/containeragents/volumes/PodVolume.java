package com.microsoft.azure.containeragents.volumes;

import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.Volume;

import java.io.Serializable;

/**
 * Created by chenyl on 7/21/2017.
 */
public abstract class PodVolume extends AbstractDescribableImpl<PodVolume> implements Serializable {

    private static final long serialVersionUID = 8874521354L;

    public abstract String getMountPath();

    public abstract Volume buildVolume(String volumeNmae);
}
