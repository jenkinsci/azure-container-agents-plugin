package com.microsoft.jenkins.containeragents.builders;

import com.microsoft.jenkins.containeragents.PodEnvVar;
import com.microsoft.jenkins.containeragents.PodImagePullSecrets;
import com.microsoft.jenkins.containeragents.strategy.ContainerIdleRetentionStrategy;
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy;
import com.microsoft.jenkins.containeragents.volumes.AzureDiskVolume;
import com.microsoft.jenkins.containeragents.volumes.AzureFileVolume;
import com.microsoft.jenkins.containeragents.volumes.EmptyDirVolume;
import com.microsoft.jenkins.containeragents.volumes.HostPathVolume;
import com.microsoft.jenkins.containeragents.volumes.PersistentVolumeClaim;
import com.microsoft.jenkins.containeragents.volumes.PodVolume;
import com.microsoft.jenkins.containeragents.volumes.SecretVolume;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PodTemplateFluent<T extends PodTemplateFluent<T>> {

    private String name;

    private String description;

    private String image;

    private String command;

    private String args;

    private String label;

    private String rootFs;

    private RetentionStrategy<?> retentionStrategy;

    private boolean privileged;

    private String specifyNode;

    private String requestCpu;

    private String limitCpu;

    private String requestMemory;

    private String limitMemory;

    private List<PodEnvVar> envVars = new ArrayList<>();

    private List<PodVolume> volumes = new ArrayList<>();

    private List<PodImagePullSecrets> imagePullSecrets = new ArrayList<>();

    private List<DockerRegistryEndpoint> privateRegistryCredentials = new ArrayList<>();

    public PodTemplateFluent() {
        this.image = "jenkinsci/jnlp-slave";
        this.args = "-url ${rootUrl} ${secret} ${nodeName}";
        this.rootFs = "/jenkins";
        this.retentionStrategy = new ContainerOnceRetentionStrategy();
        this.specifyNode = "";
    }

    //CHECKSTYLE:OFF
    public T withName(String name) {
        this.name = name;
        return (T) this;
    }

    public T withDescription(String description) {
        this.description = description;
        return (T) this;
    }

    public T withImage(String image) {
        this.image = image;
        return (T) this;
    }

    public T withCommand(String command) {
        this.command = command;
        return (T) this;
    }

    public T withArgs(String args) {
        this.args = args;
        return (T) this;
    }

    public T withLabel(String label) {
        this.label = label;
        return (T) this;
    }

    public T withRootFs(String rootFs) {
        this.rootFs = rootFs;
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

    public T withPrivileged(boolean privileged) {
        this.privileged = privileged;
        return (T) this;
    }

    public T withSpecifyNode(String specifyNode) {
        this.specifyNode = specifyNode;
        return (T) this;
    }

    public T withRequestCpu(String requestCpu) {
        this.requestCpu = requestCpu;
        return (T) this;
    }

    public T withLimitCpu(String limitCpu) {
        this.limitCpu = limitCpu;
        return (T) this;
    }

    public T withRequestMemory(String requestMemory) {
        this.requestMemory = requestMemory;
        return (T) this;
    }

    public T withLimitMemory(String limitMemory) {
        this.limitMemory = limitMemory;
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

    public T addToVolumes(PodVolume... volumes) {
        this.volumes.addAll(Arrays.asList(volumes));
        return (T) this;
    }

    public T addNewAzureDiskVolume(String mountPath, String diskName, String diskUrl) {
        this.volumes.add(new AzureDiskVolume(mountPath, diskName, diskUrl));
        return (T) this;
    }

    public T addNewAzureFileVolume(String mountPath, String secretName, String shareName, boolean readOnly) {
        this.volumes.add(new AzureFileVolume(mountPath, secretName, shareName, readOnly));
        return (T) this;
    }

    public T addNewEmptyVolume(String mountPath, boolean inMemory) {
        this.volumes.add(new EmptyDirVolume(mountPath, inMemory));
        return (T) this;
    }

    public T addNewHostPathVolume(String mountPath, String hostPath) {
        this.volumes.add(new HostPathVolume(mountPath, hostPath));
        return (T) this;
    }

    public T addNewPersistentVolumeClaim(String mountPath, String claimName, boolean readOnly) {
        this.volumes.add(new PersistentVolumeClaim(mountPath, claimName, readOnly));
        return (T) this;
    }

    public T addNewSecretVolume(String mountPath, String secretName) {
        this.volumes.add(new SecretVolume(mountPath, secretName));
        return (T) this;
    }

    public T addToImagePullSecrets(PodImagePullSecrets... imagePullSecrets) {
        this.imagePullSecrets.addAll(Arrays.asList(imagePullSecrets));
        return (T) this;
    }

    public T addNewImagePullSecret(String secretName) {
        this.imagePullSecrets.add(new PodImagePullSecrets(secretName));
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
    //CHECKSTYLE:ON

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    public String getCommand() {
        return command;
    }

    public String getArgs() {
        return args;
    }

    public String getLabel() {
        return label;
    }

    public String getRootFs() {
        return rootFs;
    }

    public RetentionStrategy<?> getRetentionStrategy() {
        return retentionStrategy;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    public String getSpecifyNode() {
        return specifyNode;
    }

    public String getRequestCpu() {
        return requestCpu;
    }

    public String getLimitCpu() {
        return limitCpu;
    }

    public String getRequestMemory() {
        return requestMemory;
    }

    public String getLimitMemory() {
        return limitMemory;
    }

    public List<PodEnvVar> getEnvVars() {
        return envVars;
    }

    public List<PodVolume> getVolumes() {
        return volumes;
    }

    public List<PodImagePullSecrets> getImagePullSecrets() {
        return imagePullSecrets;
    }

    public List<DockerRegistryEndpoint> getPrivateRegistryCredentials() {
        return privateRegistryCredentials;
    }
}
