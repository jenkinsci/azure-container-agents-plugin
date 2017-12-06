package com.microsoft.jenkins.containeragents.remote;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.microsoft.jenkins.azurecommons.Constants;
import com.microsoft.jenkins.azurecommons.remote.SSHClient;
import com.microsoft.jenkins.containeragents.helper.RetryTask;
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
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SSHLauncher extends ComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(SSHLauncher.class.getName());
    private static final int RETRY_LIMIT = 3;
    private static final int RETRY_INTERVAL = 10;

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

        final PrintStream logger = listener.getLogger();

        LOGGER.log(Level.INFO, "SSHLauncher: Start to connect node {0} : {1} via SSH",
                new Object[]{node.getDisplayName(), host});
        try {
            SSHClient sshClient = new RetryTask<SSHClient>(new Callable<SSHClient>() {
                @Override
                public SSHClient call() throws Exception {
                    return new SSHClient(host, port, credentials).connect().withLogger(logger);
                }
            }, new SSHRetryStrategy(RETRY_LIMIT, RETRY_INTERVAL)).call();

            InputStream inputStream = new ByteArrayInputStream(Jenkins.getInstance().getJnlpJars("slave.jar")
                    .readFully());
            sshClient.copyTo(inputStream, "slave.jar");
            LOGGER.log(Level.INFO, "SSHLauncher: Copy slave.jar to remote host successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SSHLauncher: Copy slave.jar to remote host failed");
            computer.setAcceptingTasks(false);
            throw new InterruptedException(e.toString());
        }

        // A lot of codes copied from commons-plugin as there is a classloader issue.
        // JSch class in commons-plugin conflict with the same class in maven-plugin
        // Will reuse commons-plugin whenever it moved to a jar package.
        Session session = null;
        try {
            session = new RetryTask<Session>(new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    Session session = getSession(credentials, host, port);

                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    if (credentials instanceof StandardUsernamePasswordCredentials) {
                        session.setPassword(((StandardUsernamePasswordCredentials) credentials)
                                .getPassword().getPlainText());
                    }

                    session.connect();
                    return session;
                }
            }, new SSHRetryStrategy(RETRY_LIMIT, RETRY_INTERVAL)).call();

            final ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            final String execCommand = "java -jar slave.jar";
            channelExec.setCommand(execCommand);
            channelExec.connect();

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
            LOGGER.log(Level.INFO, "SSHLauncher: launched agent successfully");
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "SSHLauncher: launching agent failed");
            if (session != null) {
                session.disconnect();
            }
            computer.setAcceptingTasks(false);
            throw new InterruptedException(e.toString());
        }

    }

    private Session getSession(StandardUsernameCredentials credentials, String host, int port) throws JSchException {
        JSch jsch = new JSch();
        if (credentials instanceof SSHUserPrivateKey) {
            SSHUserPrivateKey sshUserPrivateKey = (SSHUserPrivateKey) credentials;
            String passphrase = sshUserPrivateKey.getPassphrase() == null
                    ? null
                    : sshUserPrivateKey.getPassphrase().getPlainText();
            byte[] passphraseBytes = passphrase == null ? null : passphrase.getBytes(Charset.forName("UTF-8"));

            int seq = 0;
            for (String privateKey : sshUserPrivateKey.getPrivateKeys()) {
                String name = sshUserPrivateKey.getUsername();
                if (seq++ != 0) {
                    name += "-" + seq;
                }
                jsch.addIdentity(name, privateKey.getBytes(Constants.UTF8), null, passphraseBytes);
            }
        }

        return jsch.getSession(credentials.getUsername(), host, port);
    }

}
