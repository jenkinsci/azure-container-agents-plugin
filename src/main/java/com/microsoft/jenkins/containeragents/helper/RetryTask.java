package com.microsoft.jenkins.containeragents.helper;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RetryTask<T> implements Callable<T> {
    private static final Logger LOGGER = Logger.getLogger(RetryTask.class.getName());

    private final Callable<T> task;

    private final IRetryStrategy retryStrategy;

    public RetryTask(Callable<T> task, IRetryStrategy retryStrategy) {
        this.task = task;
        this.retryStrategy = retryStrategy;
    }

    @Override
    public T call() throws Exception {
        while (true) {
            try {
                return task.call();
            } catch (Exception e) {
                if (retryStrategy.canRetry(e)) {
                    LOGGER.log(Level.INFO, "RetryTask: handleRetry due to: ", e);
                    retryStrategy.handleRetry(e);
                } else {
                    LOGGER.log(Level.WARNING, "RetryTask: Cannot handleRetry due to: ", e);
                    throw e;
                }
            }
        }
    }
}
