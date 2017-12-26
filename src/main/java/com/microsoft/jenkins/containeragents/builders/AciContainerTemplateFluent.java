package com.microsoft.jenkins.containeragents.builders;

import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.aci.AciPort;
import com.microsoft.jenkins.containeragents.aci.volumes.AzureFileVolume;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AciContainerTemplateFluent<T extends AciContainerTemplateFluent<T>> {

    private String name;

    private String label;

    private String image;

    private String osType;

    private String command;

    private String rootFs;

    private int timeout;

    private List<AciPort> ports = new ArrayList<>();

    private String cpu;

    private String memory;

    private RetentionStrategy<?> retentionStrategy;

    private List<PodEnvVar> envVars = new ArrayList<>();

    private List<DockerRegistryEndpoint> privateRegistryCredentials = new ArrayList<>();

    private List<AzureFileVolume> volumes = new ArrayList<>();

    private String launchMethodType;

    private String sshCredentialsId;

    private String sshPort;

    //CHECKSTYLE:OFF
    AciContainerTemplateFluent() {
        timeout = 10;
        osType = "Linux";
        image = "jenkinsci/jnlp-slave";
        command = "jenkins-slave -url ${rootUrl} ${secret} ${nodeName}";
        rootFs = "/home/jenkins";
        retentionStrategy = new ContainerOnceRetentionStrategy();
        cpu = "1";
        memory = "1.5";
        launchMethodType = Constants.LAUNCH_METHOD_JNLP;
    }

    public T withName(String name) {
        this.name = name;
        return (T) this;
    }

    public T withLabel(String label) {
        this.label = label;
        return (T) this;
    }

    public T withImage(String image) {
        this.image = image;
        return (T) this;
    }

    public T withOsType(String osType) {
        this.osType = osType;
        return (T) this;
    }

    public T withCommand(String command) {
        this.command = command;
        return (T) this;
    }

    public T withRootFs(String rootFs) {
        this.rootFs = rootFs;
        return (T) this;
    }

    public T withTimeout(int timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    public T withPorts(List<AciPort> ports) {
        this.ports.clear();
        this.ports.addAll(ports);
        return (T) this;
    }

    public T addToPorts(AciPort... ports) {
        this.ports.addAll(Arrays.asList(ports));
        return (T) this;
    }

    public T addNewPort(String port) {
        this.ports.add(new AciPort(port));
        return (T) this;
    }

    public T withCpu(String cpu) {
        this.cpu = cpu;
        return (T) this;
    }

    public T withMemory(String memory) {
        this.memory = memory;
        return (T) this;
    }

    public T withOnceRetentionStrategy() {
        this.retentionStrategy = new ContainerOnceRetentionStrategy();
        return (T) this;
    }

    public T withIdleRetentionStrategy(int idle) {
        this.retentionStrategy = new ContainerIdleRetentionStrategy(idle);
        return (T) this;
    }

    public T withEnvVars(List<PodEnvVar> envVars) {
        this.envVars.clear();
        this.envVars.addAll(envVars);
        return (T) this;
    }

    public T addToEnvVars(PodEnvVar... envVars) {
        this.envVars.addAll(Arrays.asList(envVars));
        return (T) this;
    }

    public T addNewEnvVar(String key, String value) {
        this.envVars.add(new PodEnvVar(key, value));
        return (T) this;
    }

    public T withPrivateRegistryCredentials(List<DockerRegistryEndpoint> privateRegistryCredentials) {
        this.privateRegistryCredentials.clear();
        this.privateRegistryCredentials.addAll(privateRegistryCredentials);
        return (T) this;
    }

    public T addToPrivateRegistryCredentials(DockerRegistryEndpoint... privateRegistryCredentials) {
        this.privateRegistryCredentials.addAll(Arrays.asList(privateRegistryCredentials));
        return (T) this;
    }

    public T addNewPrivateRegistryCredential(String registryUrl, String credentialsId) {
        this.privateRegistryCredentials.add(new DockerRegistryEndpoint(registryUrl, credentialsId));
        return (T) this;
    }

    public T withVolume(List<AzureFileVolume> volumes) {
        this.volumes.clear();
        this.volumes.addAll(volumes);
        return (T) this;
    }

    public T addToVolumes(AzureFileVolume... volumes) {
        this.volumes.addAll(Arrays.asList(volumes));
        return (T) this;
    }

    public T addNewAzureFileVolume(String mountPath, String shareName, String credentialsId) {
        this.volumes.add(new AzureFileVolume(mountPath, shareName, credentialsId));
        return (T) this;
    }

    public T withJNLPLaunchMethod() {
        this.launchMethodType = Constants.LAUNCH_METHOD_JNLP;
        return (T) this;
    }

    public T withSSHLaunchMethod(String sshCredentialsId, String sshPort) {
        this.launchMethodType = Constants.LAUNCH_METHOD_SSH;
        this.sshCredentialsId = sshCredentialsId;
        this.sshPort = sshPort;
        return (T) this;
    }
    //CHECKSTYLE:ON

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getImage() {
        return image;
    }

    public String getOsType() {
        return osType;
    }

    public String getCommand() {
        return command;
    }

    public String getRootFs() {
        return rootFs;
    }

    public int getTimeout() {
        return timeout;
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

    public String getLaunchMethodType() {
        return launchMethodType;
    }

    public String getSshCredentialsId() {
        return sshCredentialsId;
    }

    public String getSshPort() {
        return sshPort;
    }
}
