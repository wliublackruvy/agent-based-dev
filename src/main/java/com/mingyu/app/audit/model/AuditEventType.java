package com.mingyu.app.audit.model;

// Implements 4.会员审计功能

public enum AuditEventType {
    DEVICE_UNLOCK,
    DAILY_UNLOCK_TOTAL,
    APP_USAGE,
    MONITORING_VIEW,
    SENSITIVE_ACTION
}