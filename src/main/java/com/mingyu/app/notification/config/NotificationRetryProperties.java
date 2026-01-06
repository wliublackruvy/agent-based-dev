package com.mingyu.app.notification.config;

// Implements 2.权限引导与存活看板

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notification.retry")
public class NotificationRetryProperties {

    private int maxAttempts = 3;
    private long initialBackoffMillis = 250;
    private double multiplier = 2.0d;
    private long maxBackoffMillis = 5_000;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBackoffMillis() {
        return initialBackoffMillis;
    }

    public void setInitialBackoffMillis(long initialBackoffMillis) {
        this.initialBackoffMillis = initialBackoffMillis;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxBackoffMillis() {
        return maxBackoffMillis;
    }

    public void setMaxBackoffMillis(long maxBackoffMillis) {
        this.maxBackoffMillis = maxBackoffMillis;
    }

    public void validate() {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoffMillis < 0) {
            throw new IllegalArgumentException("initialBackoffMillis must be positive");
        }
        if (maxBackoffMillis < initialBackoffMillis) {
            throw new IllegalArgumentException(
                    "maxBackoffMillis must be greater than or equal to initialBackoffMillis");
        }
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be at least 1.0");
        }
    }
}