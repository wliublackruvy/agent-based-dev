package com.mingyu.app.notification.provider;

// Implements 2.权限引导与存活看板

import com.mingyu.app.notification.model.NotificationChannel;
import com.mingyu.app.notification.model.NotificationDispatchRequest;

public interface NotificationChannelProvider {

    NotificationChannel getChannel();

    default boolean supports(NotificationDispatchRequest request) {
        return true;
    }

    void send(NotificationDispatchRequest request, String traceId);
}