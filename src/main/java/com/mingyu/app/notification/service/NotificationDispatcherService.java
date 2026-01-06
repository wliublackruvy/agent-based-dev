package com.mingyu.app.notification.service;

// Implements 2.权限引导与存活看板

import com.mingyu.app.notification.config.NotificationRetryProperties;
import com.mingyu.app.notification.exception.NotificationDeliveryException;
import com.mingyu.app.notification.exception.NotificationDispatchException;
import com.mingyu.app.notification.model.NotificationChannel;
import com.mingyu.app.notification.model.NotificationDispatchRequest;
import com.mingyu.app.notification.model.NotificationDispatchResult;
import com.mingyu.app.notification.provider.NotificationChannelProvider;
import com.mingyu.app.notification.support.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcherService {

    private static final Duration MAX_TRIGGER_DELAY = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcherService.class);

    private final List<NotificationChannelProvider> channelProviders;
    private final NotificationRetryProperties retryProperties;
    private final Clock clock;
    private final Sleeper sleeper;

    public NotificationDispatcherService(
            List<NotificationChannelProvider> channelProviders,
            NotificationRetryProperties retryProperties,
            Clock clock,
            Sleeper sleeper) {
        this.channelProviders = sortProviders(channelProviders);
        this.retryProperties = Objects.requireNonNull(retryProperties, "retryProperties is required");
        this.retryProperties.validate();
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper is required");
    }

    public NotificationDispatchResult dispatch(NotificationDispatchRequest request, String traceId) {
        NotificationDispatchRequest safeRequest = Objects.requireNonNull(request, "request is required");
        String safeTraceId = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        guardTriggerWindow(safeRequest.getTriggeredAt(), safeTraceId);

        if (channelProviders.isEmpty()) {
            throw new NotificationDispatchException("No notification channel providers configured");
        }

        NotificationDeliveryException lastError = null;
        boolean providerMatched = false;

        for (NotificationChannelProvider provider : channelProviders) {
            if (!provider.supports(safeRequest)) {
                continue;
            }

            providerMatched = true;
            NotificationChannel channel =
                    Objects.requireNonNull(provider.getChannel(), "Notification channel must not be null");

            for (int attempt = 1; attempt <= retryProperties.getMaxAttempts(); attempt++) {
                try {
                    provider.send(safeRequest, safeTraceId);
                    log.info(
                            "Trace [{}] delivered {} notification via {} after {} attempt(s)",
                            safeTraceId,
                            safeRequest.getEventType(),
                            channel,
                            attempt);
                    return NotificationDispatchResult.success(channel, attempt, safeTraceId, clock.instant());
                } catch (RuntimeException ex) {
                    lastError = asDeliveryException(channel, ex);
                    log.warn(
                            "Trace [{}] attempt {}/{} failed on channel {} - {}",
                            safeTraceId,
                            attempt,
                            retryProperties.getMaxAttempts(),
                            channel,
                            lastError.getMessage());
                    if (attempt < retryProperties.getMaxAttempts()) {
                        waitForNextAttempt(channel, attempt);
                    }
                }
            }
        }

        if (!providerMatched) {
            throw new NotificationDispatchException(
                    "No notification channel provider can handle event " + safeRequest.getEventType(), lastError);
        }

        if (lastError == null) {
            lastError = new NotificationDeliveryException("All channels failed without providing an error");
        }

        throw new NotificationDispatchException(
                "Unable to deliver notification for trace " + safeTraceId, lastError);
    }

    private void guardTriggerWindow(Instant triggeredAt, String traceId) {
        Instant safeTriggeredAt = Objects.requireNonNull(triggeredAt, "triggeredAt is required");
        Instant now = clock.instant();
        Duration delay = Duration.between(safeTriggeredAt, now);
        if (delay.isNegative()) {
            return;
        }
        if (delay.compareTo(MAX_TRIGGER_DELAY) > 0) {
            throw new NotificationDispatchException(
                    "Trace "
                            + traceId
                            + " violated invocation window with delay "
                            + delay.toSeconds()
                            + "s",
                    null);
        }
    }

    private void waitForNextAttempt(NotificationChannel channel, int attempt) {
        long sleepMillis = computeBackoffMillis(attempt);
        if (sleepMillis <= 0) {
            return;
        }
        try {
            sleeper.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new NotificationDispatchException(
                    "Retry interrupted for channel " + channel + " on attempt " + (attempt + 1), interruptedException);
        }
    }

    private long computeBackoffMillis(int attempt) {
        double exponent = Math.max(0, attempt - 1);
        double rawDelay =
                retryProperties.getInitialBackoffMillis() * Math.pow(retryProperties.getMultiplier(), exponent);
        long boundedDelay = (long) Math.min(rawDelay, retryProperties.getMaxBackoffMillis());
        return Math.max(0L, boundedDelay);
    }

    private NotificationDeliveryException asDeliveryException(NotificationChannel channel, RuntimeException exception) {
        if (exception instanceof NotificationDeliveryException deliveryException) {
            return deliveryException;
        }
        return new NotificationDeliveryException(
                "Channel " + channel + " failed with " + exception.getClass().getSimpleName(), exception);
    }

    private List<NotificationChannelProvider> sortProviders(List<NotificationChannelProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<NotificationChannelProvider> copy = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(copy);
        return List.copyOf(copy);
    }
}