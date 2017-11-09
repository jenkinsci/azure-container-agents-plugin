package com.microsoft.jenkins.containeragents.aci;

import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import com.microsoft.jenkins.containeragents.util.Constants;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AciAgent extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(AciAgent.class.getName());

    private final String credentialsId;

    private final String cloudName;

    private final String resourceGroup;

    private String deployName = null;

    @DataBoundConstructor
    public AciAgent(AciCloud cloud, AciContainerTemplate template) throws Descriptor.FormException, IOException {
        super(generateAgentName(template),
                "",
                template.getRootFs(),
                1,
                Mode.NORMAL,
                template.getLabel(),
                new JNLPLauncher(),
                template.getRetentionStrategy(),
                Collections.<NodeProperty<Node>>emptyList());
        this.credentialsId = cloud.getCredentialsId();
        this.cloudName = cloud.getName();
        this.resourceGroup = cloud.getResourceGroup();
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new AciComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        final Computer computer = toComputer();
        if (computer == null || StringUtils.isEmpty(cloudName)) {
            return;
        }
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            return;
        }
        if (!(cloud instanceof AciCloud)) {
            String msg = String.format("Cloud %s is not a AciCloud", cloudName);
            LOGGER.log(Level.WARNING, msg);
            listener.fatalError(msg);
            return;
        }

        Computer.threadPoolForRemoting.execute(new Runnable() {
            @Override
            public void run() {
                AciService.deleteAciContainerGroup(credentialsId,
                        resourceGroup,
                        AciAgent.this.getNodeName(),
                        deployName);
            }
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
    public Node reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Aci Agent";
        };

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

}
