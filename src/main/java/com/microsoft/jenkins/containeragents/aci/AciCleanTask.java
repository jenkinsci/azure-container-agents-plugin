package com.microsoft.jenkins.containeragents.aci;


import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.GenericResource;
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AciCleanTask extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(AciCleanTask.class.getName());

    private static final int RECURRENCE_PERIOD_IN_MILLIS = 30 * 60 * 1000;
    private static final long SUCCESSFUL_DEPLOYMENT_TIMEOUT_IN_MINUTES = 60;
    private static final long FAILING_DEPLOYMENT_TIMEOUT_IN_MINUTES = 60 * 8;

    public AciCleanTask() {
        super("ACI Period Clean Task");
    }

    private void clean() {
        Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) {
            return;
        }
        for (AciCloud cloud : instance.clouds.getAll(AciCloud.class)) {
            cleanLeakedContainer(cloud);
        }
    }

    private static class DeploymentInfo implements Serializable {
        DeploymentInfo(String cloudName,
                       String resourceGroupName,
                       String deploymentName,
                       int deleteAttempts) {
            this.cloudName = cloudName;
            this.deploymentName = deploymentName;
            this.resourceGroupName = resourceGroupName;
            this.attemptsRemaining = deleteAttempts;
        }

        String getCloudName() {
            return cloudName;
        }

        String getDeploymentName() {
            return deploymentName;
        }

        String getResourceGroupName() {
            return resourceGroupName;
        }

        boolean hasAttemptsRemaining() {
            return attemptsRemaining > 0;
        }

        void decrementAttemptsRemaining() {
            attemptsRemaining--;
        }

        private final String cloudName;
        private final String deploymentName;
        private final String resourceGroupName;
        private int attemptsRemaining;
    }

    public static class DeploymentRegistrar {
        private static final String OUTPUT_FILE
                = Paths.get(loadProperty("JENKINS_HOME"), "aci-deployment.out").toString();

        private static final DeploymentRegistrar DEPLOYMENT_REGISTRAR = new DeploymentRegistrar();

        private static final int MAX_DELETE_ATTEMPTS = 3;

        private ConcurrentLinkedQueue<DeploymentInfo> deploymentsToClean =
                new ConcurrentLinkedQueue<>();

        protected DeploymentRegistrar() {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(OUTPUT_FILE))) {
                deploymentsToClean = (ConcurrentLinkedQueue<DeploymentInfo>) ois.readObject();
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.WARNING,
                        "AzureAciCleanUpTask: readResolve: Cannot open deployment output file");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "AzureAciCleanUpTask: readResolve: Cannot deserialize deploymentsToClean", e);
            }
        }

        public static DeploymentRegistrar getInstance() {
            return DEPLOYMENT_REGISTRAR;
        }

        public ConcurrentLinkedQueue<DeploymentInfo> getDeploymentsToClean() {
            return deploymentsToClean;
        }

        public void registerDeployment(String cloudName,
                                       String resourceGroupName,
                                       String deploymentName) {
            LOGGER.log(Level.INFO,
                    "AzureAciCleanUpTask: registerDeployment: Registering deployment {0} in {1}",
                    new Object[]{deploymentName, resourceGroupName});
            DeploymentInfo newDeploymentToClean =
                    new DeploymentInfo(cloudName, resourceGroupName, deploymentName, MAX_DELETE_ATTEMPTS);
            deploymentsToClean.add(newDeploymentToClean);

            syncDeploymentsToClean();
        }

        public synchronized void syncDeploymentsToClean() {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(OUTPUT_FILE))) {
                oos.writeObject(deploymentsToClean);
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.WARNING,
                        "AzureAciCleanUpTask: registerDeployment: Cannot open deployment output file"
                                + OUTPUT_FILE);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "AzureAciCleanUpTask: registerDeployment: Serialize failed", e);
            }
        }
    }

    public static String loadProperty(final String name) {
        final String value = System.getProperty(name);
        if (StringUtils.isBlank(value)) {
            return loadEnv(name);
        }
        return value;
    }

    public static String loadEnv(final String name) {
        final String value = System.getenv(name);
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value;
    }

    public AciCloud getCloud(String cloudName) {
        return Jenkins.getInstanceOrNull() == null ? null : (AciCloud) Jenkins.get().getCloud(cloudName);
    }

    public void cleanDeployments() {
        cleanDeployments(SUCCESSFUL_DEPLOYMENT_TIMEOUT_IN_MINUTES, FAILING_DEPLOYMENT_TIMEOUT_IN_MINUTES);
    }

    @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "TODO needs triage")
    private void cleanDeployments(long successTimeoutInMinutes, long failTimeoutInMinutes) {
        DeploymentInfo firstBackInQueue = null;
        ConcurrentLinkedQueue<DeploymentInfo> deploymentsToClean
                = DeploymentRegistrar.getInstance().getDeploymentsToClean();
        while (!deploymentsToClean.isEmpty() && firstBackInQueue != deploymentsToClean.peek()) {
            DeploymentInfo info = deploymentsToClean.remove();

            LOGGER.log(getNormalLoggingLevel(),
                    "AzureAciCleanUpTask: cleanDeployments: Checking deployment {0}",
                    info.getDeploymentName());

            AciCloud cloud = getCloud(info.getCloudName());

            if (cloud == null) {
                // Cloud could have been deleted, skip
                continue;
            }


            try {

                AzureResourceManager azureClient = AzureContainerUtils.getAzureClient(cloud.getCredentialsId());

                // This will throw if the deployment can't be found.  This could happen in a couple instances
                // 1) The deployment has already been deleted
                // 2) The deployment doesn't exist yet (race between creating the deployment and it
                //    being accepted by Azure.
                // To avoid this, we implement a retry.  If we hit an exception, we will decrement the number
                // of retries.  If we hit 0, we remove the deployment from our list.
                Deployment deployment;
                try {
                    deployment = azureClient.deployments().
                            getByResourceGroup(info.getResourceGroupName(), info.getDeploymentName());
                } catch (NullPointerException e) {
                    LOGGER.log(getNormalLoggingLevel(),
                            "AzureAciCleanUpTask: cleanDeployments: Deployment not found, skipping");
                    continue;
                }
                if (deployment == null) {
                    LOGGER.log(getNormalLoggingLevel(),
                            "AzureAciCleanUpTask: cleanDeployments: Deployment not found, skipping");
                    continue;
                }

                OffsetDateTime deploymentTime = deployment.timestamp();
                LOGGER.log(getNormalLoggingLevel(),
                        "AzureAciCleanUpTask: cleanDeployments: Deployment created on {0}",
                        deploymentTime.toString());

                long diffTimeInMinutes = ChronoUnit.MINUTES
                        .between(deploymentTime, OffsetDateTime.now());

                String state = deployment.provisioningState();

                if (!"succeeded".equalsIgnoreCase(state) && diffTimeInMinutes > failTimeoutInMinutes) {
                    LOGGER.log(getNormalLoggingLevel(),
                            "AzureAciCleanUpTask: cleanDeployments: "
                                    + "Failed deployment older than {0} minutes, deleting",
                            failTimeoutInMinutes);
                    // Delete the deployment
                    azureClient.deployments()
                            .deleteByResourceGroup(info.getResourceGroupName(), info.getDeploymentName());
                } else if ("succeeded".equalsIgnoreCase(state)
                        && diffTimeInMinutes > successTimeoutInMinutes) {
                    LOGGER.log(getNormalLoggingLevel(),
                            "AzureAciCleanUpTask: cleanDeployments: "
                                    + "Successful deployment older than {0} minutes, deleting",
                            successTimeoutInMinutes);
                    // Delete the deployment
                    azureClient.deployments()
                            .deleteByResourceGroup(info.getResourceGroupName(), info.getDeploymentName());
                } else {
                    LOGGER.log(getNormalLoggingLevel(),
                            "AzureAciCleanUpTask: cleanDeployments: Deployment newer than timeout, keeping");

                    if (firstBackInQueue == null) {
                        firstBackInQueue = info;
                    }
                    // Put it back
                    deploymentsToClean.add(info);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "AzureAciCleanUpTask: cleanDeployments: Failed to get/delete deployment",
                        e);
                // Check the number of attempts remaining. If greater than 0, decrement
                // and add back into the queue.
                if (info.hasAttemptsRemaining()) {
                    info.decrementAttemptsRemaining();

                    if (firstBackInQueue == null) {
                        firstBackInQueue = info;
                    }

                    // Put it back in the queue for another attempt
                    deploymentsToClean.add(info);
                }
            }
        }
        DeploymentRegistrar.getInstance().syncDeploymentsToClean();
    }

    private void cleanLeakedContainer(final AciCloud cloud) {
        LOGGER.log(Level.INFO, "Starting to clean leaked containers for cloud " + cloud.getName());
        AzureResourceManager azureClient;
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

        for (final GenericResource resource : azureClient.genericResources().listByResourceGroup(resourceGroup)) {
            if (resource.resourceProviderNamespace().equalsIgnoreCase("Microsoft.ContainerInstance")
                    && resource.resourceType().equalsIgnoreCase("containerGroups")
                    && resource.tags().containsKey("JenkinsInstance")
                    && resource.tags().get("JenkinsInstance")
                    .equalsIgnoreCase(Jenkins.get().getLegacyInstanceId())) {
                if (!validContainerSet.contains(resource.name())) {
                    AciCloud.getThreadPool().submit(() -> AciService.deleteAciContainerGroup(credentialsId,
                            resourceGroup,
                            resource.name(),
                            null));
                }
            }
        }
    }

    private Set<String> getValidContainer() {
        Set<String> result = new TreeSet<>();
        for (Computer computer : Jenkins.get().getComputers()) {
            if (computer instanceof AciComputer) {
                result.add(computer.getName());
            }
        }
        return result;
    }

    @Override
    public void execute(TaskListener arg0) {
        clean();
        cleanDeployments();
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD_IN_MILLIS;
    }

}
