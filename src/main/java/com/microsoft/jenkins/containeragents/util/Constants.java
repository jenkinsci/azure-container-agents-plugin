package com.microsoft.jenkins.containeragents.util;

public final class Constants {

    public static final String NODE_MASTER = "master";

    public static final String NODE_ROLE = "role";

    public static final String NODE_ROLE_AKS = "kubernetes.io/role";

    public static final String AI_CONTAINER_AGENT = "ContainerAgent";

    public static final String AI_CONTAINER_NAME = "AcsContainerName";

    public static final String AI_ACS_CREDENTIALS_TYPE = "AcsCredentialsType";

    public static final String AI_ACS_TYPE_SSH = "SSH Username with private key";

    public static final String AI_ACS_TYPE_CONFIG = "Microsoft Azure Container Service Credential";

    public static final String AI_ACS_MASTER_FQDN = "Acs Master FQDN";

    public static final String AI_ACI_NAME = "AciName";

    public static final String AI_ACI_DEPLOYMENT_NAME = "AciDeploymentName";

    public static final String AI_ACI_AGENT = "AciAgent";

    public static final String AI_ACI_CPU_CORE = "CpuCores";

    public static final int KUBERNETES_RANDOM_NAME_LENGTH = 5;

    public static final int ACI_RANDOM_NAME_LENGTH = 5;

    public static final int ACI_DEPLOYMENT_RANDOM_NAME_LENGTH = 8;

    public static final int ACI_VOLUME_NAME_LENGTH = 3;

    public static final String AKS_NAMESPACE = "Microsoft.ContainerService";

    public static final String AKS_RESOURCE_TYPE = "managedClusters";

    public static final String LAUNCH_METHOD_SSH = "ssh";

    public static final String LAUNCH_METHOD_JNLP = "jnlp";

    public static final int DEFAULT_SSH_PORT = 22;

    public static final int SSH_PORT_MIN = 0;

    public static final int SSH_PORT_MAX = 65535;

    private Constants() {

    }
}
