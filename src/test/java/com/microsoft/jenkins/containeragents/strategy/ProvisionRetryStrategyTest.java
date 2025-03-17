package com.microsoft.jenkins.containeragents.strategy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvisionRetryStrategyTest {

    private final ProvisionRetryStrategy strategy = new ProvisionRetryStrategy();

    @Test
    void testSuccess() {
        final String templateName = "TEMPLATE1";
        strategy.getRecords().put(templateName, new ProvisionRetryStrategy.Record());
        assertTrue(strategy.getRecords().containsKey(templateName));
        strategy.success(templateName);
        assertFalse(strategy.getRecords().containsKey(templateName));
    }

    @Test
    void testFailure() {
        final String templateName = "TEMPLATE2";
        strategy.failure(templateName);
        ProvisionRetryStrategy.Record record = strategy.getRecords().get(templateName);
        assertNotNull(record);
        final int interval = record.getInterval();
        assertThat(interval, greaterThan(0));
        strategy.failure(templateName);
        assertEquals(interval * 2, record.getInterval());
    }

    @Test
    void testIsEnabled() {
        final String templateName = "TEMPLATE3";
        assertTrue(strategy.isEnabled(templateName));
        strategy.failure(templateName);
        assertFalse(strategy.isEnabled(templateName));
        strategy.success(templateName);
        assertTrue(strategy.isEnabled(templateName));


        strategy.failure(templateName);
        assertFalse(strategy.isEnabled(templateName));
        long interval = strategy.getNextRetryTime(templateName) - System.currentTimeMillis();
        try {
            Thread.sleep(interval + 500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(strategy.isEnabled(templateName));
    }
}
