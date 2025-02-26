package com.microsoft.jenkins.containeragents.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProvisionRetryStrategy {
    private static final int MAX_INTERVAL = 10 * 60 * 1000;    // 10 minutes
    private Map<String, Record> records = new ConcurrentHashMap<>();

    public synchronized void failure(String name) {
        Record record = records.get(name);
        if (record == null) {
            record = new Record();
            records.put(name, record);
        } else {
            int nextInterval = record.getInterval();
            nextInterval = Math.min(nextInterval * 2, MAX_INTERVAL);
            record.setInterval(nextInterval);
        }
        record.setLastFail(System.currentTimeMillis());
    }

    public synchronized void success(String name) {
        records.remove(name);
    }

    public long getNextRetryTime(String name) {
        Record record = records.get(name);
        if (record == null) {
            return 0;
        }
        return record.getLastFail() + record.getInterval();
    }

    public boolean isEnabled(String name) {
        return isEnabled(name, System.currentTimeMillis());
    }

    public boolean isEnabled(String name, long now) {
        return now >= getNextRetryTime(name);
    }

    Map<String, Record> getRecords() {
        return records;
    }

    public static class Record {
        private static final int INITIAL_INTERVAL = 5 * 1000;   // 5 sec.
        private long lastFail;
        private int interval = INITIAL_INTERVAL;

        public long getLastFail() {
            return lastFail;
        }

        public void setLastFail(long lastFail) {
            this.lastFail = lastFail;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }
    }
}
