package com.mingyu.app.notification.exception;

// Implements 2.权限引导与存活看板

public class NotificationDispatchException extends RuntimeException {

    public NotificationDispatchException(String message) {
        super(message);
    }

    public NotificationDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}