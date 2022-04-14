package com.microsoft.jenkins.containeragents.aci;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.microsoft.jenkins.containeragents.Messages;
import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.remote.LaunchMethodTypeContent;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import com.microsoft.jenkins.containeragents.aci.volumes.AzureFileVolume;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.Collections;
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

    private AciPrivateIpAddress privateIpAddress;

    private int timeout;

    private List<AciPort> ports;

    private String cpu;

    private String memory;

    private RetentionStrategy<?> retentionStrategy;

    private List<PodEnvVar> envVars = new ArrayList<>();

    private List<DockerRegistryEndpoint> privateRegistryCredentials = new ArrayList<>();

    private List<AzureFileVolume> volumes = new ArrayList<>();

    private String launchMethodType;

    private String sshCredentialsId;

    private String sshPort;

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
        this.image = image.trim();
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

    public boolean isJnlp() {
        return StringUtils.isBlank(launchMethodType) || launchMethodType.equals(Constants.LAUNCH_METHOD_JNLP);
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

    public String getLaunchMethodType() {
        return StringUtils.defaultString(launchMethodType, Constants.LAUNCH_METHOD_JNLP);
    }

    @DataBoundSetter
    public void setLaunchMethodType(String launchMethodType) {
        this.launchMethodType = StringUtils.defaultString(launchMethodType, Constants.LAUNCH_METHOD_JNLP);
    }

    public String getSshCredentialsId() {
        return StringUtils.defaultString(sshCredentialsId);
    }

    public String getSshPort() {
        return StringUtils.defaultString(sshPort);
    }

    @DataBoundSetter
    public void setLaunchMethodTypeContent(LaunchMethodTypeContent launchMethodTypeContent) {
        if (launchMethodTypeContent != null) {
            this.sshCredentialsId = StringUtils.defaultString(launchMethodTypeContent.getSshCredentialsId());
            this.sshPort = StringUtils.defaultString(launchMethodTypeContent.getSshPort(), "22");
        }
    }
    public AciPrivateIpAddress getPrivateIpAddress() {
        return privateIpAddress;
    }

    @DataBoundSetter
    public void setPrivateIpAddress(AciPrivateIpAddress privateIpAddress) {
        this.privateIpAddress = privateIpAddress;
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

        // Return null because it's a static dropDownList.
        public ListBoxModel doFillLaunchMethodTypeItems() {
            return null;
        }

        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.add("--- Select Azure Container Service Credentials ---", "");
            listBoxModel.withAll(CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class,
                    owner,
                    ACL.SYSTEM,
                    Collections.<DomainRequirement>emptyList()));
            return listBoxModel;
        }

        public FormValidation doCheckSshPort(@QueryParameter String value) {
            if (StringUtils.isBlank(value) || value.matches("^[0-9]*$")
                    && Integer.parseInt(value) >= Constants.SSH_PORT_MIN
                    && Integer.parseInt(value) <= Constants.SSH_PORT_MAX) {
                return FormValidation.ok();
            }
            return FormValidation.error(Messages.Not_Number_Error());
        }
    }
}
