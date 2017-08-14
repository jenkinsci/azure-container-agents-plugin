/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents.strategy;


import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.EphemeralNode;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;


import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContainerOnceRetentionStrategy extends CloudRetentionStrategy implements ExecutorListener {
    private static final Logger LOGGER = Logger.getLogger(ContainerOnceRetentionStrategy.class.getName());
    private static final transient int IDLE_MINUTES = 10;
    private static final transient int WAIT_TIME = 10 * 1000;

    @DataBoundConstructor
    public ContainerOnceRetentionStrategy() {
        super(IDLE_MINUTES);
    }

    public int getIdleMinutes() {
        return IDLE_MINUTES;
    }

    @Override
    public long check(final AbstractCloudComputer c) {
        // When the slave is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (c.isIdle() && !disabled) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(IDLE_MINUTES)) {
                LOGGER.log(Level.INFO, "Disconnecting {0}", c.getName());
                done(c);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(AbstractCloudComputer c) {
        if (c.getNode() instanceof EphemeralNode) {
            throw new IllegalStateException("May not use OnceRetentionStrategy on an EphemeralNode: " + c);
        }
        super.start(c);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    private void done(Executor executor) {
        try {
            Thread.sleep(WAIT_TIME);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
        final AbstractCloudComputer<?> c = (AbstractCloudComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();

        LOGGER.log(Level.INFO, "terminating {0} since {1} seems to be finished", new Object[] {c.getName(), exec});
        done(c);
    }

    private void done(final AbstractCloudComputer<?> c) {
        c.setAcceptingTasks(false); // just in case
        synchronized (this) {
            Computer.threadPoolForRemoting.submit(new Runnable() {
                @Override
                public void run() {
                    Queue.withLock(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AbstractCloudSlave node = c.getNode();
                                if (node != null) {
                                    node.terminate();
                                }
                            } catch (InterruptedException | IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to terminate {0}: {1}",
                                        new Object[]{c.getName(), e});
                            }
                        }
                    });
                }
            });
        }

    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();


    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Container Once Retention Strategy";
        }
    }

}
