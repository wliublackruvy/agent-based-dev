package com.mingyu.app.notification.model;

// Implements 2.权限引导与存活看板

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NotificationDispatchRequest {

    private final NotificationEventType eventType;
    private final NotificationRecipient recipient;
    private final Instant triggeredAt;
    private final String message;
    private final Map<String, Object> attributes;

    private NotificationDispatchRequest(Builder builder) {
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType is required");
        this.recipient = Objects.requireNonNull(builder.recipient, "recipient is required");
        this.triggeredAt = Objects.requireNonNull(builder.triggeredAt, "triggeredAt is required");
        this.message = Objects.requireNonNull(builder.message, "message is required");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public NotificationRecipient getRecipient() {
        return recipient;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private NotificationEventType eventType;
        private NotificationRecipient recipient;
        private Instant triggeredAt;
        private String message;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder() {}

        public Builder eventType(NotificationEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder recipient(NotificationRecipient recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder triggeredAt(Instant triggeredAt) {
            this.triggeredAt = triggeredAt;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                attributes.forEach((key, value) -> {
                    if (key != null && value != null) {
                        this.attributes.put(key, value);
                    }
                });
            }
            return this;
        }

        public NotificationDispatchRequest build() {
            return new NotificationDispatchRequest(this);
        }
    }
}