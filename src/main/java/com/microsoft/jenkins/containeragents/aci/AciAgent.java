package com.microsoft.jenkins.containeragents.aci;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.microsoft.jenkins.containeragents.remote.ISSHLaunchable;
import com.microsoft.jenkins.containeragents.remote.SSHLauncher;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AciAgent extends AbstractCloudSlave implements ISSHLaunchable, TrackedItem {
    private static final Logger LOGGER = Logger.getLogger(AciAgent.class.getName());

    private final String credentialsId;

    private final String cloudName;

    private final String resourceGroup;

    private String deployName = null;

    private final String sshCredentialsId;

    private final String sshPort;

    private final String launchType;

    private String host;

    private final ProvisioningActivity.Id provisioningId;

    @DataBoundConstructor
    public AciAgent(AciCloud cloud, AciContainerTemplate template) throws Descriptor.FormException, IOException {
        super(generateAgentName(template), template.getRootFs(),
                template.getLaunchMethodType().equals(Constants.LAUNCH_METHOD_JNLP)
                ? new JNLPLauncher(true)
                : new SSHLauncher());

        setLabelString(template.getLabel());
        setRetentionStrategy(template.getRetentionStrategy());

        this.credentialsId = cloud.getCredentialsId();
        this.cloudName = cloud.getName();
        this.resourceGroup = cloud.getResourceGroup();
        this.sshCredentialsId = template.getSshCredentialsId();
        this.sshPort = template.getSshPort();
        this.launchType = template.getLaunchMethodType();

        this.provisioningId = new ProvisioningActivity.Id(cloud.name, template.getName(), getNodeName());
    }

    @Override
    public AciComputer createComputer() {
        return new AciComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final Computer computer = toComputer();
        if (computer == null || StringUtils.isEmpty(cloudName)) {
            return;
        }
        final Cloud cloud = Jenkins.get().getCloud(cloudName);
        if (cloud == null) {
            return;
        }
        if (!(cloud instanceof AciCloud)) {
            String msg = String.format("Cloud %s is not a AciCloud", cloudName);
            LOGGER.log(Level.WARNING, msg);
            listener.fatalError(msg);
            return;
        }

        Computer.threadPoolForRemoting.execute(() -> {
            AciService.deleteAciContainerGroup(credentialsId,
                    resourceGroup,
                    AciAgent.this.getNodeName(),
                    deployName);
        });
    }

    static String generateAgentName(AciContainerTemplate template) {
        return AzureContainerUtils.generateName(template.getName(), Constants.ACI_RANDOM_NAME_LENGTH);
    }

    @DataBoundSetter
    public void setDeployName(String deployName) {
        this.deployName = deployName;
    }

    public String getDeployName() {
        return deployName;
    }

    @Override
    public Node reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        return this;
    }

    @Override
    public StandardUsernameCredentials getSshCredential() throws IllegalArgumentException {
        StandardUsernameCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernameCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.withId(sshCredentialsId));
        if (credentials == null) {
            throw new IllegalArgumentException("Could not find credentials with id: " + sshCredentialsId);
        }

        return credentials;
    }

    @Override
    public int getSshPort() {
        return Integer.parseInt(sshPort);
    }

    @Override
    public boolean isSshLaunchType() {
        return launchType.equals(Constants.LAUNCH_METHOD_SSH);
    }

    @Override
    public String getHost() {
        return StringUtils.defaultString(host);
    }

    public void setHost(String host) {
        this.host = host;
    }

    @NonNull
    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Aci Agent";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

}
