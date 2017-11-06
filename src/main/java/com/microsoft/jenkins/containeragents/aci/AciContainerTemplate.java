package com.microsoft.jenkins.containeragents.aci;

import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import com.microsoft.jenkins.containeragents.aci.volumes.AzureFileVolume;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel;
import org.apache.commons.lang3.time.StopWatch;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class AciContainerTemplate extends AbstractDescribableImpl<AciContainerTemplate> {

    private static final Logger LOGGER = Logger.getLogger(AciContainerTemplate.class.getName());


    private String name;

    private String label;

    private String image;

    private String osType;

    private String command;

    private String rootFs;

    private int timeout;

    private List<AciPort> ports;

    private String cpu;

    private String memory;

    private RetentionStrategy<?> retentionStrategy;

    private List<PodEnvVar> envVars = new ArrayList<>();

    private List<DockerRegistryEndpoint> privateRegistryCredentials = new ArrayList<>();

    private List<AzureFileVolume> volumes = new ArrayList<>();

    private boolean isAvailable = true;

    @DataBoundConstructor
    public AciContainerTemplate(String name,
                                String label,
                                int timeout,
                                String osType,
                                String image,
                                String command,
                                String rootFs,
                                List<AciPort> ports,
                                List<DockerRegistryEndpoint> privateRegistryCredentials,
                                List<PodEnvVar> envVars,
                                List<AzureFileVolume> volumes,
                                RetentionStrategy<?> retentionStrategy,
                                String cpu,
                                String memory) {
        this.name = name;
        this.label = label;
        this.image = image;
        this.osType = osType;
        this.command = command;
        this.rootFs = rootFs;
        if (ports == null) {
            this.ports = new ArrayList<>();
        } else {
            this.ports = ports;
        }
        this.cpu = cpu;
        this.memory = memory;
        this.timeout = timeout;
        this.retentionStrategy = retentionStrategy;
        if (envVars == null) {
            this.envVars = new ArrayList<>();
        } else {
            this.envVars = envVars;
        }
        if (privateRegistryCredentials == null) {
            this.privateRegistryCredentials = new ArrayList<>();
        } else {
            this.privateRegistryCredentials = privateRegistryCredentials;
        }
        if (volumes == null) {
            this.volumes = new ArrayList<>();
        } else {
            this.volumes = volumes;
        }
        setAvailable(true);
    }

    public void provisionAgents(AciCloud cloud, AciAgent agent, StopWatch stopWatch) throws Exception {
        AciService.createDeployment(cloud, this, agent, stopWatch);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getImage() {
        return image;
    }

    public String getOsType() {
        return osType;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getCommand() {
        return command;
    }

    public String getRootFs() {
        return rootFs;
    }

    public List<AciPort> getPorts() {
        return ports;
    }

    public String getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    public RetentionStrategy<?> getRetentionStrategy() {
        return retentionStrategy;
    }

    public List<PodEnvVar> getEnvVars() {
        return envVars;
    }

    public List<DockerRegistryEndpoint> getPrivateRegistryCredentials() {
        return privateRegistryCredentials;
    }

    public List<AzureFileVolume> getVolumes() {
        return volumes;
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
    }

    public boolean getAvailable() {
        return isAvailable;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AciContainerTemplate> {

        @Override
        public String getDisplayName() {
            return "Aci Container Template";
        }

        public List<Descriptor<RetentionStrategy<?>>> getAciRetentionStrategyDescriptors() {
            List<Descriptor<RetentionStrategy<?>>> list = new ArrayList<>();
            list.add(ContainerOnceRetentionStrategy.DESCRIPTOR);
            list.add(ContainerIdleRetentionStrategy.DESCRIPTOR);
            return list;
        }

        public ListBoxModel doFillOsTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Linux");
            model.add("Windows");
            return model;
        }
    }
}
