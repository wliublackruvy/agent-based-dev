package com.mingyu.app.notification.service;

// Implements 2.权限引导与存活看板

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mingyu.app.notification.config.NotificationRetryProperties;
import com.mingyu.app.notification.exception.NotificationDeliveryException;
import com.mingyu.app.notification.exception.NotificationDispatchException;
import com.mingyu.app.notification.model.NotificationChannel;
import com.mingyu.app.notification.model.NotificationDispatchRequest;
import com.mingyu.app.notification.model.NotificationDispatchResult;
import com.mingyu.app.notification.model.NotificationEventType;
import com.mingyu.app.notification.model.NotificationRecipient;
import com.mingyu.app.notification.provider.NotificationChannelProvider;
import com.mingyu.app.notification.support.Sleeper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherServiceTest {

    @Mock
    private NotificationChannelProvider pushProvider;

    @Mock
    private NotificationChannelProvider webhookProvider;

    @Mock
    private Sleeper sleeper;

    private NotificationRetryProperties retryProperties;
    private Clock clock;
    private NotificationDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        retryProperties = new NotificationRetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setInitialBackoffMillis(100);
        retryProperties.setMaxBackoffMillis(1_000);
        retryProperties.setMultiplier(2.0d);

        clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        dispatcherService =
                new NotificationDispatcherService(List.of(pushProvider, webhookProvider), retryProperties, clock, sleeper);
    }

    @Test
    void dispatchRetriesWithinBackoffWindow() throws Exception {
        NotificationDispatchRequest request = buildRequest(clock.instant());

        when(pushProvider.supports(any())).thenReturn(true);
        when(pushProvider.getChannel()).thenReturn(NotificationChannel.PUSH);
        doThrow(new NotificationDeliveryException("temporary outage"))
                .doNothing()
                .when(pushProvider)
                .send(any(NotificationDispatchRequest.class), anyString());
        doNothing().when(sleeper).sleep(100L);

        NotificationDispatchResult result = dispatcherService.dispatch(request, "trace-retry");

        assertEquals(NotificationChannel.PUSH, result.getChannel());
        assertEquals(2, result.getAttempts());
        verify(sleeper).sleep(100L);
        verifyNoInteractions(webhookProvider);
    }

    @Test
    void dispatchFallsBackToSecondaryChannelAfterFailures() throws Exception {
        retryProperties.setMaxAttempts(2);
        dispatcherService =
                new NotificationDispatcherService(List.of(pushProvider, webhookProvider), retryProperties, clock, sleeper);

        NotificationDispatchRequest request = buildRequest(clock.instant());

        when(pushProvider.supports(any())).thenReturn(true);
        when(pushProvider.getChannel()).thenReturn(NotificationChannel.PUSH);
        doThrow(new NotificationDeliveryException("perm outage"))
                .when(pushProvider)
                .send(any(NotificationDispatchRequest.class), anyString());
        when(webhookProvider.supports(any())).thenReturn(true);
        when(webhookProvider.getChannel()).thenReturn(NotificationChannel.WEBHOOK);
        doNothing().when(webhookProvider).send(any(NotificationDispatchRequest.class), anyString());
        doNothing().when(sleeper).sleep(100L);

        NotificationDispatchResult result = dispatcherService.dispatch(request, "trace-fallback");

        assertEquals(NotificationChannel.WEBHOOK, result.getChannel());
        assertEquals(1, result.getAttempts());
        verify(pushProvider, times(2)).send(any(NotificationDispatchRequest.class), anyString());
        verify(webhookProvider).send(any(NotificationDispatchRequest.class), anyString());
        verify(sleeper).sleep(100L);
    }

    @Test
    void dispatchRejectsRequestsOutsideInvocationWindow() {
        NotificationDispatchRequest request =
                buildRequest(clock.instant().minusSeconds(61));

        assertThrows(
                NotificationDispatchException.class,
                () -> dispatcherService.dispatch(request, "trace-late"));
        verifyNoInteractions(pushProvider);
        verifyNoInteractions(webhookProvider);
    }

    private NotificationDispatchRequest buildRequest(Instant triggeredAt) {
        NotificationRecipient recipient = NotificationRecipient.builder()
                .monitorUserId(10L)
                .monitoredUserId(20L)
                .deviceToken("ios-token")
                .phoneNumber("+15555550123")
                .webhookUrl("https://hook.mingyu.com/notify")
                .build();
        return NotificationDispatchRequest.builder()
                .eventType(NotificationEventType.PERMISSION_LOSS)
                .recipient(recipient)
                .triggeredAt(triggeredAt)
                .message("Permission revoked")
                .attribute("priority", "critical")
                .build();
    }
}