/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.containeragents;


import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.microsoft.azure.containeragents.KubernetesService.lookupCredentials;

@Extension
public class KubernetesCleanUpTask extends AsyncPeriodicWork {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCleanUpTask.class.getName());

    private static final int RECURRENCE_PERIOD_IN_MILLIS = 15 * 60 * 1000;

    private static final int CLEAN_TIMEOUT = 15;

    public KubernetesCleanUpTask() {
        super("Kubernetes Period Clean Task");
    }

    private void clean() throws AuthenticationException {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            return;
        }
         for (KubernetesCloud cloud : instance.clouds.getAll(KubernetesCloud.class)) {
            if (cloud.getClient() == null) {
                return;
            }
            cleanLeakedPods(cloud, cloud.getMasterFqdn(), cloud.getAcsCredentialsId(), cloud.getNamespace());
        }
    }

    private void cleanLeakedPods(final KubernetesCloud cloud,
                                 final String masterFqdn,
                                 final String acsCredentialsId,
                                 final String namespace) throws AuthenticationException {
        KubernetesClient client = cloud.getClient();

        if (client == null) {
            return;
        }

        try {
            List<Pod> pods = client.pods().inNamespace(namespace).withLabel(PodTemplate.LABEL_KEY, PodTemplate.LABEL_VALUE).list().getItems();

            if (pods.isEmpty()) {
                return;
            }

            List<Pod> podsToRemove = new ArrayList<>();

            final List<String> validPods = getValidPods(cloud);

            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                boolean shouldSkip = false;
                for (String validPod : validPods) {
                    if (podName.equalsIgnoreCase(validPod)) {
                        shouldSkip = true;
                        break;
                    }
                }
                if (!shouldSkip) {
                    podsToRemove.add(pod);
                }
            }

            for (Pod pod : podsToRemove) {
                ((KubernetesCloud) cloud).deletePod(pod.getMetadata().getName());
            }
        } catch (Exception e) {
            LOGGER.error("KubernetesCleanUpTask: cleanLeakedPods: failed", e);
        }

    }


    @Override
    public void execute(TaskListener arg0) throws InterruptedException {
        LOGGER.info("KubernetesCleanUpTask: execute: Clean task start");

        Callable<Void> cleanTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                clean();
                return null;
            }
        };
        ExecutorService es =  KubernetesCloud.getThreadPool();

        Future<Void> result = es.submit(cleanTask);
        LOGGER.info("Now poolsize is: {}", ((ThreadPoolExecutor) es).getPoolSize());
        LOGGER.info("Now corepoolsize is: {}", ((ThreadPoolExecutor) es).getCorePoolSize());
        LOGGER.info("Now TaskCount is: {}", ((ThreadPoolExecutor) es).getTaskCount());

        try {
            LOGGER.info("KubernetesCleanUpTask: execute: Running clean task within 15 minute timeout");
            result.get(CLEAN_TIMEOUT, TimeUnit.MINUTES);
        } catch (Exception e) {
            LOGGER.error("KubernetesCleanUpTask: execute: failed ", e);
        }
    }


    private List<String> getValidPods(Cloud cloud) {
        List<String> validPodsName = new ArrayList<>();

        Jenkins instance = Jenkins.getInstance();
        if (instance != null) {
            for (Computer computer : instance.getComputers()) {
                if (computer instanceof KubernetesComputer) {
                    KubernetesComputer kubernetesComputer = (KubernetesComputer) computer;
                    KubernetesAgent agent = kubernetesComputer.getNode();
                    if (agent != null && agent.getCloudName().equalsIgnoreCase(cloud.name)) {
                        validPodsName.add(kubernetesComputer.getName());
                    }
                }
            }
        }
        return validPodsName;
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD_IN_MILLIS;
    }
}
