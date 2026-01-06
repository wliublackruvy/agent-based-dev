package com.mingyu.app.common.error;

// Implements System

import com.example.auth.exception.InvalidOtpException;
import com.example.auth.exception.RateLimitException;
import com.mingyu.app.logging.LoggingConstants;
import com.mingyu.app.notification.exception.NotificationDispatchException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String traceId = ensureTraceId();
        List<ApiErrorResponse.FieldValidationError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(this::toFieldError)
                        .toList();
        log.warn("Trace {} validation failed with {} field error(s)", traceId, errors.size());
        return ApiErrorResponse.validation(traceId, errors);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBindException(BindException ex) {
        String traceId = ensureTraceId();
        List<ApiErrorResponse.FieldValidationError> errors =
                ex.getFieldErrors().stream()
                        .map(this::toFieldError)
                        .toList();
        log.warn("Trace {} bind failure with {} field error(s)", traceId, errors.size());
        return ApiErrorResponse.validation(traceId, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String traceId = ensureTraceId();
        List<ApiErrorResponse.FieldValidationError> errors =
                ex.getConstraintViolations().stream()
                        .map(this::toFieldError)
                        .toList();
        log.warn("Trace {} constraint violation: {}", traceId, ex.getMessage());
        return ApiErrorResponse.validation(traceId, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = ensureTraceId();
        log.warn("Trace {} parameter {} type mismatch", traceId, ex.getName());
        ApiErrorResponse.FieldValidationError detail =
                new ApiErrorResponse.FieldValidationError(ex.getName(), "Invalid value");
        return ApiErrorResponse.validation(traceId, List.of(detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMissingParameter(MissingServletRequestParameterException ex) {
        String traceId = ensureTraceId();
        log.warn("Trace {} missing request parameter {}", traceId, ex.getParameterName());
        ApiErrorResponse.FieldValidationError detail =
                new ApiErrorResponse.FieldValidationError(ex.getParameterName(), "Parameter is required");
        return ApiErrorResponse.validation(traceId, List.of(detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadable(HttpMessageNotReadableException ex) {
        return generalClientError("MALFORMED_PAYLOAD", "Malformed request payload", ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return generalClientError("METHOD_NOT_ALLOWED", ex.getMessage(), ex);
    }

    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiErrorResponse handleRateLimit(RateLimitException ex) {
        return generalClientError("RATE_LIMIT_EXCEEDED", ex.getMessage(), ex);
    }

    @ExceptionHandler(InvalidOtpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidOtp(InvalidOtpException ex) {
        return generalClientError("INVALID_OTP", ex.getMessage(), ex);
    }

    @ExceptionHandler(NotificationDispatchException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleNotificationDispatch(NotificationDispatchException ex) {
        return serverError("NOTIFICATION_DISPATCH_FAILED", ex.getMessage(), ex);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnhandled(Exception ex, HttpServletRequest request) {
        return serverError("INTERNAL_SERVER_ERROR", "Unexpected server error", ex, request);
    }

    private ApiErrorResponse generalClientError(String code, String message, Exception ex) {
        String traceId = ensureTraceId();
        if (ex == null) {
            log.warn("Trace {} {} - {}", traceId, code, message);
        } else {
            log.warn("Trace {} {} - {} ({})", traceId, code, message, ex.getMessage());
        }
        return ApiErrorResponse.general(traceId, code, message);
    }

    private ApiErrorResponse serverError(String code, String message, Exception ex) {
        return serverError(code, message, ex, null);
    }

    private ApiErrorResponse serverError(String code, String message, Exception ex, HttpServletRequest request) {
        String traceId = ensureTraceId();
        if (request == null) {
            log.error("Trace {} {} - {}", traceId, code, message, ex);
        } else {
            log.error(
                    "Trace {} {} {} responded with {} ({})",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    code,
                    message,
                    ex);
        }
        return ApiErrorResponse.general(traceId, code, message);
    }

    private ApiErrorResponse.FieldValidationError toFieldError(FieldError fieldError) {
        String fieldName = fieldError != null ? fieldError.getField() : "field";
        String message = fieldError != null && StringUtils.hasText(fieldError.getDefaultMessage())
                ? fieldError.getDefaultMessage()
                : "Invalid value";
        return new ApiErrorResponse.FieldValidationError(fieldName, message);
    }

    private ApiErrorResponse.FieldValidationError toFieldError(ConstraintViolation<?> violation) {
        String fieldName = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "field";
        String message = StringUtils.hasText(violation.getMessage()) ? violation.getMessage() : "Invalid value";
        return new ApiErrorResponse.FieldValidationError(fieldName, message);
    }

    private String ensureTraceId() {
        // Ensures every error payload receives a trace identifier even if filters fail to populate MDC.
        String traceId = MDC.get(LoggingConstants.MDC_TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
            MDC.put(LoggingConstants.MDC_TRACE_ID, traceId);
        }
        return traceId;
    }
}