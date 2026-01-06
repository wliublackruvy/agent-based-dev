package com.mingyu.app.logging;

// Implements System

public final class LoggingConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_DEVICE_ID = "deviceId";

    private LoggingConstants() {}
}