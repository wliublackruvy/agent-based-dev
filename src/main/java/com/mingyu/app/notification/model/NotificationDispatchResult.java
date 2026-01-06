package com.mingyu.app.notification.model;

// Implements 2.权限引导与存活看板

import java.time.Instant;
import java.util.Objects;

public final class NotificationDispatchResult {

    private final NotificationChannel channel;
    private final int attempts;
    private final String traceId;
    private final Instant completedAt;

    private NotificationDispatchResult(NotificationChannel channel, int attempts, String traceId, Instant completedAt) {
        this.channel = Objects.requireNonNull(channel, "channel is required");
        this.attempts = attempts;
        this.traceId = Objects.requireNonNull(traceId, "traceId is required");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    public static NotificationDispatchResult success(
            NotificationChannel channel, int attempts, String traceId, Instant completedAt) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be greater than zero");
        }
        return new NotificationDispatchResult(channel, attempts, traceId, completedAt);
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}