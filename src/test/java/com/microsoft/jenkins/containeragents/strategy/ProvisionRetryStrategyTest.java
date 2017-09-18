package com.microsoft.jenkins.containeragents.strategy;

import com.microsoft.jenkins.containeragents.strategy.ProvisionRetryStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.greaterThan;


public class ProvisionRetryStrategyTest {

    ProvisionRetryStrategy strategy = new ProvisionRetryStrategy();

    @Before
    public void setUp() {

    }

    @Test
    public void testSuccess() {
        final String templateName = "TEMPLATE1";
        strategy.getRecords().put(templateName, new ProvisionRetryStrategy.Record());
        Assert.assertTrue(strategy.getRecords().containsKey(templateName));
        strategy.success(templateName);
        Assert.assertFalse(strategy.getRecords().containsKey(templateName));
    }

    @Test
    public void testFailure() {
        final String templateName = "TEMPLATE2";
        strategy.failure(templateName);
        ProvisionRetryStrategy.Record record = strategy.getRecords().get(templateName);
        Assert.assertNotNull(record);
        final int interval = record.getInterval();
        Assert.assertThat(interval, greaterThan(0));
        strategy.failure(templateName);
        Assert.assertEquals(interval * 2, record.getInterval());
    }

    @Test
    public void testIsEnabled() {
        final String templateName = "TEMPLATE3";
        Assert.assertTrue(strategy.isEnabled(templateName));
        strategy.failure(templateName);
        Assert.assertFalse(strategy.isEnabled(templateName));
        strategy.success(templateName);
        Assert.assertTrue(strategy.isEnabled(templateName));


        strategy.failure(templateName);
        Assert.assertFalse(strategy.isEnabled(templateName));
        long interval = strategy.getNextRetryTime(templateName) - System.currentTimeMillis();
        try {
            Thread.sleep(interval + 500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Assert.assertTrue(strategy.isEnabled(templateName));
    }
}
