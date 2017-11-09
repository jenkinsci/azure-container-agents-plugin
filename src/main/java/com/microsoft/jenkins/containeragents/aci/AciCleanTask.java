package com.microsoft.jenkins.containeragents.aci;


import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.GenericResource;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AciCleanTask extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(AciCleanTask.class.getName());

    private static final int RECURRENCE_PERIOD_IN_MILLIS = 30 * 60 * 1000;

    public AciCleanTask() {
        super("ACI Period Clean Task");
    }

    private void clean()   {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            return;
        }
        for (AciCloud cloud : instance.clouds.getAll(AciCloud.class)) {
            cleanLeakedContainer(cloud);
        }
    }

    private void cleanLeakedContainer(final AciCloud cloud)   {
        LOGGER.log(Level.INFO, "Starting to clean leaked containers for cloud " + cloud.getName());
        Azure azureClient = null;
        try {
            azureClient = cloud.getAzureClient();
        } catch (Exception e) {
            return;
        }

        final String resourceGroup = cloud.getResourceGroup();
        final String credentialsId = cloud.getCredentialsId();
        if (StringUtils.isBlank(resourceGroup) || StringUtils.isBlank(credentialsId)) {
            return;
        }

        Set<String> validContainerSet = getValidContainer();

        List<GenericResource> resourceList = azureClient.genericResources().listByResourceGroup(resourceGroup);
        for (final GenericResource resource : resourceList) {
            if (resource.resourceProviderNamespace().equalsIgnoreCase("Microsoft.ContainerInstance")
                    && resource.resourceType().equalsIgnoreCase("containerGroups")
                    && resource.tags().containsKey("JenkinsInstance")
                    && resource.tags().get("JenkinsInstance")
                        .equalsIgnoreCase(Jenkins.getInstance().getLegacyInstanceId())) {
                if (!validContainerSet.contains(resource.name())) {
                    AciCloud.getThreadPool().submit(new Runnable() {
                        @Override
                        public void run() {
                            AciService.deleteAciContainerGroup(credentialsId,
                                    resourceGroup,
                                    resource.name(),
                                    null);
                        }
                    });
                }
            }
        }
    }

    private Set<String> getValidContainer() {
        Set<String> result = new TreeSet<>();
        if (Jenkins.getInstance() != null) {
            for (Computer computer : Jenkins.getInstance().getComputers()) {
                if (computer instanceof AciComputer) {
                    result.add(computer.getName());
                }
            }
        }
        return result;
    }


    @Override
    public void execute(TaskListener arg0) {
        clean();
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD_IN_MILLIS;
    }

}
