package com.mingyu.app.common.error;

// Implements System

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ApiErrorResponse {

    private final String traceId;
    private final String code;
    private final String message;
    private final Instant timestamp;
    private final List<FieldValidationError> errors;

    private ApiErrorResponse(String traceId, String code, String message, List<FieldValidationError> errors) {
        this.traceId = Objects.requireNonNull(traceId, "traceId is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.timestamp = Instant.now();
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ApiErrorResponse general(String traceId, String code, String message) {
        return new ApiErrorResponse(traceId, code, message, List.of());
    }

    public static ApiErrorResponse validation(String traceId, List<FieldValidationError> errors) {
        return new ApiErrorResponse(traceId, "VALIDATION_ERROR", "Request validation failed", errors);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<FieldValidationError> getErrors() {
        return errors;
    }

    public static final class FieldValidationError {
        private final String field;
        private final String message;

        public FieldValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}