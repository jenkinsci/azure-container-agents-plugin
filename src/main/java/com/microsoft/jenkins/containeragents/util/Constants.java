package com.microsoft.jenkins.containeragents.util;

public final class Constants {

    public static final String AI_ACI_NAME = "AciName";

    public static final String AI_ACI_DEPLOYMENT_NAME = "AciDeploymentName";

    public static final String AI_ACI_AGENT = "AciAgent";

    public static final String AI_ACI_CPU_CORE = "CpuCores";

    public static final int ACI_RANDOM_NAME_LENGTH = 5;

    public static final int ACI_DEPLOYMENT_RANDOM_NAME_LENGTH = 8;

    public static final int ACI_VOLUME_NAME_LENGTH = 3;

    public static final String LAUNCH_METHOD_SSH = "ssh";

    public static final String LAUNCH_METHOD_JNLP = "jnlp";

    public static final int SSH_PORT_MIN = 0;

    public static final int SSH_PORT_MAX = 65535;

    public static final int MILLIS_IN_SECOND = 1000;

    public static final int MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;

    private Constants() {

    }
}
