/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.jenkins.containeragents.strategy;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import net.jcip.annotations.GuardedBy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class ContainerIdleRetentionStrategy extends CloudRetentionStrategy {
    private static final Logger LOGGER = Logger.getLogger(ContainerIdleRetentionStrategy.class.getName());

    private int idleMinutes;
    private static final transient int LAPSE = 5;

    @DataBoundConstructor
    public ContainerIdleRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
        this.idleMinutes = idleMinutes;
    }

    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(final AbstractCloudComputer c) {
        final AbstractCloudSlave computerNode = c.getNode();
        if (c.isIdle() && !disabled && computerNode != null) {
            final long milliBetweenCreationAndIdle = c.getIdleStartMilliseconds() - c.getConnectTime();
            boolean neverConnected = milliBetweenCreationAndIdle < TimeUnit.SECONDS.toMillis(LAPSE);

            // neverConnected will always be true after jenkins restart so that slave will not be deleted
            // So overwrite neverConnected if slave exists after restart
            Computer computer = Jenkins.get().toComputer();
            if (computer == null) {
                return 1;
            }
            if (c.getIdleStartMilliseconds() - computer.getConnectTime()
                    < TimeUnit.SECONDS.toMillis(LAPSE)) {
                neverConnected = false;
            }

            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMinutes != 0
                    && !neverConnected
                    && idleMilliseconds > TimeUnit.MINUTES.toMillis(idleMinutes)) {
                LOGGER.log(Level.INFO, "Disconnecting {0}", c.getName());
                try {
                    computerNode.terminate();
                } catch (InterruptedException | IOException e) {
                    LOGGER.log(WARNING, "Failed to terminate " + c.getName(), e);
                }
            }
        }
        return 1;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();


    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Container Idle Retention Strategy";
        }
    }
}
