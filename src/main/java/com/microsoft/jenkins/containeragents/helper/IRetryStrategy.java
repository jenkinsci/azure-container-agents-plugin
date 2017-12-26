package com.microsoft.jenkins.containeragents.helper;

public interface IRetryStrategy {
    void handleRetry(Exception e);
    boolean canRetry(Exception e);
}
