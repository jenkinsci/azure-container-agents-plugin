package com.microsoft.jenkins.containeragents.strategy;

import java.util.HashMap;
import java.util.Map;

public class ProvisionRetryStrategy {
    private static final int MAX_INTERVAL = 600;    // 10 minutes
    private Map<String, Record> records = new HashMap<>();

    public void failure(String name) {
        Record record = records.get(name);
        if (record == null) {
            record = new Record();
            records.put(name, record);
        }
        record.setLastFail(System.currentTimeMillis());
        int nextInterval = record.getInterval();
        nextInterval = (nextInterval * 2 > MAX_INTERVAL) ? MAX_INTERVAL : nextInterval * 2;
        record.setInterval(nextInterval);
    }

    public void success(String name) {
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
        return System.currentTimeMillis() > getNextRetryTime(name);
    }

    public static class Record {
        private static final int INITIAL_INTERVAL = 5;
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
