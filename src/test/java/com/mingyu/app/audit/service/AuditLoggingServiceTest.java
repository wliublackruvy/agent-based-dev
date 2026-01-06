package com.mingyu.app.audit.service;

// Implements 4.会员审计功能

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import com.mingyu.app.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLoggingServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLoggingService auditLoggingService;

    @BeforeEach
    void setUp() {
        auditLoggingService = new AuditLoggingService(auditLogRepository, new ObjectMapper());
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void logDeviceUnlockPersistsEntry() {
        Instant unlockedAt = Instant.parse("2024-06-01T08:00:00Z");

        AuditLogEntity entity =
                auditLoggingService.logDeviceUnlock(5L, 9L, "device-1", unlockedAt, "android");

        assertEquals(AuditEventType.DEVICE_UNLOCK, entity.getEventType());
        assertEquals("device-1", entity.getDeviceIdentifier());
        assertEquals("android", entity.getPlatform());
        assertEquals(1, entity.getCountValue());
        assertTrue(entity.getMetadataJson().contains("device-1"));
        verify(auditLogRepository).save(any(AuditLogEntity.class));
    }

    @Test
    void logDailyUnlockTotalStoresCount() {
        Instant day = Instant.parse("2024-06-02T00:00:00Z");

        AuditLogEntity entity = auditLoggingService.logDailyUnlockTotal(6L, 9L, day, 32);

        assertEquals(AuditEventType.DAILY_UNLOCK_TOTAL, entity.getEventType());
        assertEquals(32, entity.getCountValue());
        assertTrue(entity.getMetadataJson().contains("daily_unlock_total"));
    }

    @Test
    void logAppUsagePersistsDurations() {
        Instant start = Instant.parse("2024-06-02T02:00:00Z");
        Instant end = Instant.parse("2024-06-02T02:10:00Z");

        AuditLogEntity entity =
                auditLoggingService.logAppUsage(7L, 9L, "com.chat.app", 600, start, end, "android");

        assertEquals(AuditEventType.APP_USAGE, entity.getEventType());
        assertEquals("com.chat.app", entity.getApplicationId());
        assertEquals(600L, entity.getDurationSeconds());
        assertTrue(entity.getMetadataJson().contains("startedAt"));
    }

    @Test
    void logMonitoringViewCapturesTarget() {
        Instant viewedAt = Instant.parse("2024-06-03T09:22:00Z");

        AuditLogEntity entity =
                auditLoggingService.logMonitoringView(10L, 9L, 11L, viewedAt, "map");

        assertEquals(AuditEventType.MONITORING_VIEW, entity.getEventType());
        assertEquals(11L, entity.getTargetUserId());
        assertEquals(1, entity.getCountValue());
        assertTrue(entity.getMetadataJson().contains("map"));
    }

    @Test
    void logSensitiveActionStoresReason() {
        Instant occurredAt = Instant.parse("2024-06-03T10:10:00Z");

        AuditLogEntity entity =
                auditLoggingService.logSensitiveAction(12L, 9L, "LOCATION_TOGGLE", occurredAt, "User disabled GPS");

        assertEquals(AuditEventType.SENSITIVE_ACTION, entity.getEventType());
        assertEquals(1, entity.getCountValue());
        assertTrue(entity.getMetadataJson().contains("GPS"));
    }

    @Test
    void fetchLogsFallsBackToSimpleQueryWhenTypesMissing() {
        Instant from = Instant.parse("2024-06-04T00:00:00Z");
        Instant to = Instant.parse("2024-06-05T00:00:00Z");
        AuditLogEntity placeholder = new AuditLogEntity();
        when(auditLogRepository.findByUserIdAndEventTimeBetweenOrderByEventTimeAsc(5L, from, to))
                .thenReturn(List.of(placeholder));

        List<AuditLogEntity> result = auditLoggingService.fetchLogs(5L, from, to, Collections.emptyList());

        assertEquals(1, result.size());
        verify(auditLogRepository).findByUserIdAndEventTimeBetweenOrderByEventTimeAsc(5L, from, to);
    }

    @Test
    void fetchLogsUsesTypedQueryWhenTypesPresent() {
        Instant from = Instant.parse("2024-06-06T00:00:00Z");
        Instant to = Instant.parse("2024-06-07T00:00:00Z");
        when(auditLogRepository.findByUserIdAndEventTimeBetweenAndEventTypeInOrderByEventTimeAsc(
                        eq(5L), eq(from), eq(to), anyCollection()))
                .thenReturn(List.of());

        auditLoggingService.fetchLogs(5L, from, to, List.of(AuditEventType.APP_USAGE));

        ArgumentCaptor<Collection<AuditEventType>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(auditLogRepository)
                .findByUserIdAndEventTimeBetweenAndEventTypeInOrderByEventTimeAsc(eq(5L), eq(from), eq(to), captor.capture());
        assertTrue(captor.getValue().contains(AuditEventType.APP_USAGE));
    }
}