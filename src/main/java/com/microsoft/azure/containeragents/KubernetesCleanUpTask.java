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
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.microsoft.azure.containeragents.KubernetesService.lookupCredentials;

@Extension
public class KubernetesCleanUpTask extends AsyncPeriodicWork {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCleanUpTask.class.getName());

    private static final int RECURRENCE_PERIOD_IN_MILLIS = 1 * 60 * 1000;

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
            if (StringUtils.isBlank(cloud.getMasterFqdn())) {
                return;
            }
            cleanLeakedPods(cloud, cloud.getMasterFqdn(), cloud.getAcsCredentialsId(), cloud.getNamespace());
        }
    }

    private void cleanLeakedPods(final Cloud cloud,
                                 final String masterFqdn,
                                 final String acsCredentialsId,
                                 final String namespace) throws AuthenticationException {
        KubernetesClient client = KubernetesCloud.connect(masterFqdn, namespace, acsCredentialsId);

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
                Computer.threadPoolForRemoting.execute(() -> ((KubernetesCloud) cloud).deletePod(pod.getMetadata().getName()));
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

        Future<Void> result = KubernetesCloud.getThreadPool().submit(cleanTask);

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
