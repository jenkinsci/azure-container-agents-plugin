package com.microsoft.azure.containeragents.aci;

import com.microsoft.azure.containeragents.KubernetesAgent;
import com.microsoft.azure.containeragents.KubernetesComputer;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.JNLPLauncher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.logging.Logger;


public class AciAgent extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(AciAgent.class.getName());

    @DataBoundConstructor
    public AciAgent() throws Descriptor.FormException, IOException{
        super("", "", "", 1, Mode.NORMAL, "", new JNLPLauncher(), null, null);

    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new AciComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {

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
