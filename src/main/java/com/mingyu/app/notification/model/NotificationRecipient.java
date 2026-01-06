package com.mingyu.app.notification.model;

// Implements 2.权限引导与存活看板

import java.util.Objects;

public final class NotificationRecipient {

    private final Long monitorUserId;
    private final Long monitoredUserId;
    private final String deviceToken;
    private final String phoneNumber;
    private final String webhookUrl;

    private NotificationRecipient(Builder builder) {
        this.monitorUserId = Objects.requireNonNull(builder.monitorUserId, "monitorUserId is required");
        this.monitoredUserId = Objects.requireNonNull(builder.monitoredUserId, "monitoredUserId is required");
        this.deviceToken = builder.deviceToken;
        this.phoneNumber = builder.phoneNumber;
        this.webhookUrl = builder.webhookUrl;
    }

    public Long getMonitorUserId() {
        return monitorUserId;
    }

    public Long getMonitoredUserId() {
        return monitoredUserId;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long monitorUserId;
        private Long monitoredUserId;
        private String deviceToken;
        private String phoneNumber;
        private String webhookUrl;

        private Builder() {}

        public Builder monitorUserId(Long monitorUserId) {
            this.monitorUserId = monitorUserId;
            return this;
        }

        public Builder monitoredUserId(Long monitoredUserId) {
            this.monitoredUserId = monitoredUserId;
            return this;
        }

        public Builder deviceToken(String deviceToken) {
            this.deviceToken = deviceToken;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        public NotificationRecipient build() {
            return new NotificationRecipient(this);
        }
    }
}