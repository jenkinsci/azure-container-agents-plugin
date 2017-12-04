package com.microsoft.jenkins.containeragents.remote;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SSHLauncher extends ComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(SSHLauncher.class.getName());

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        if (computer == null) {
            LOGGER.log(Level.WARNING, "SSHLauncher: computer is null");
            return;
        }

        Slave node = computer.getNode();
        if (!(node instanceof ISSHLaunchable)) {
            LOGGER.log(Level.WARNING, "SSHLauncher: node is invalid: {0}", node);
            return;
        }
        if (!((ISSHLaunchable) node).isSshLaunchType()) {
            LOGGER.log(Level.WARNING, "SSHLauncher: node {0} is not launched by SSH", node);
            return;
        }

        final StandardUsernameCredentials credentials = ((ISSHLaunchable) node).getSshCredential();
        final int port = ((ISSHLaunchable) node).getSshPort();
        final String host = ((ISSHLaunchable) node).getHost();

        if (credentials == null || StringUtils.isBlank(host)) {
            return;
        }

        PrintStream logger = listener.getLogger();
        SSHClient sshClient = null;
        try {
            sshClient = new SSHClient(host, port, credentials).connect().withLogger(logger);
            InputStream inputStream = new ByteArrayInputStream(Jenkins.getInstance().getJnlpJars("slave.jar")
                    .readFully());
            sshClient.copyTo(inputStream, "slave.jar");

            sshClient.withChannelExec(new SSHLauncherConsumer(computer, logger));
            LOGGER.log(Level.INFO, "SSHLauncher: launched agent successfully");

        } catch (JSchException e) {
            LOGGER.log(Level.INFO, "SSHLauncher: got exception ", e);
            sshClient.close();
            computer.setAcceptingTasks(false);
        }
    }

    private class SSHLauncherConsumer implements SSHClient.ChannelExecConsumer {
        private final SlaveComputer computer;
        private final PrintStream logger;

        SSHLauncherConsumer(SlaveComputer computer, PrintStream logger) {
            this.computer = computer;
            this.logger = logger;
        }

        @Override
        public void apply(final ChannelExec channelExec) throws Exception {
            computer.setChannel(channelExec.getInputStream(),
                    channelExec.getOutputStream(),
                    logger,
                    new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    if (channelExec != null) {
                        channelExec.disconnect();
                    }
                }
            });
        }

        @Override
        public String execCommand() {
            return "java -jar slave.jar";
        }

        @Override
        public void onFinish() {
            return;
        }

    }
}
