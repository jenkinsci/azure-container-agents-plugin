package com.microsoft.azure.containeragents;


import com.microsoft.azure.containeragents.helper.AzureContainerServiceCredentials;
import com.microsoft.azure.containeragents.util.TokenCache;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.ContainerService;
import com.microsoft.azure.util.AzureCredentials;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.microsoft.azure.containeragents.KubernetesService.lookupCredentials;

public class KubernetesCleanUpTask extends AsyncPeriodicWork {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCleanUpTask.class.getName());

    private static final int RECURRENCE_PERIOD_IN_MILLIS = 60 * 60 * 1000;

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
            cleanLeakedPods(cloud.getMasterFqdn(), cloud.getAcsCredentialsId(), cloud.getNamespace());
        }
    }

    private void cleanLeakedPods(final String masterFqdn,
                                 final String acsCredentialsId,
                                 final String namespace) throws AuthenticationException {
        KubernetesClient client = KubernetesCloud.connect(masterFqdn, namespace, acsCredentialsId);

        PodList pod = client.pods().inNamespace(namespace).withLabel(PodTemplate.LABEL_KEY, PodTemplate.LABEL_VALUE).list();

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





    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD_IN_MILLIS;
    }
}
