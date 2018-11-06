package com.microsoft.jenkins.containeragents.remote;

import com.jcraft.jsch.JSchException;
import com.microsoft.jenkins.containeragents.helper.IRetryStrategy;

import java.util.concurrent.TimeUnit;

public class SSHRetryStrategy implements IRetryStrategy {

    private final int retryTimesLimit;

    private final int retryInterval;

    private int retryTimes;

    public SSHRetryStrategy(int retryTimesLimit, int retryInterval) {
        this.retryTimesLimit = retryTimesLimit;
        this.retryInterval = retryInterval;
        this.retryTimes = 0;
    }

    @Override
    public void handleRetry(Exception e) {
        retryTimes++;
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(retryInterval));
        } catch (InterruptedException ignore) {

        }
    }

    @Override
    public boolean canRetry(Exception e) {
        return e instanceof JSchException && retryTimes < retryTimesLimit;
    }
}
