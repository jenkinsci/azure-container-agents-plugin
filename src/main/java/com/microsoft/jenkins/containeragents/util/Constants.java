package com.microsoft.jenkins.containeragents.util;

public final class Constants {

    public static final String NODE_MASTER = "master";

    public static final String NODE_ROLE = "role";

    public static final String AI_CONTAINER_AGENT = "ContainerAgent";

    public static final String AI_ACS_CREDENTIALS_TYPE = "AcsCredentialsType";

    public static final String AI_ACS_TYPE_SSH = "SSH Username with private key";

    public static final String AI_ACS_TYPE_CONFIG = "Microsoft Azure Container Service Credential";

    public static final String AI_ACS_MASTER_FQDN = "Acs Master FQDN";

    public static final int KUBERNETES_RANDOM_NAME_LENGTH = 5;

    public static final int ACI_RANDOM_NAME_LENGTH = 5;

    public static final int ACI_DEPLOYMENT_RANDOM_NAME_LENGTH = 8;

    public static final int ACI_VOLUME_NAME_LENGTH = 3;

    private Constants() {

    }
}
