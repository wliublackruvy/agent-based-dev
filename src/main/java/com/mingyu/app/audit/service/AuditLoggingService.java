package com.mingyu.app.audit.service;

// Implements 4.会员审计功能

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import com.mingyu.app.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditLoggingService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLoggingService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public AuditLogEntity logDeviceUnlock(
            Long userId, Long relationshipId, String deviceIdentifier, Instant unlockedAt, String platform) {
        AuditLogEntity entry =
                baseEntry(userId, relationshipId, AuditEventType.DEVICE_UNLOCK, requireEventTime(unlockedAt));
        entry.setDeviceIdentifier(deviceIdentifier);
        entry.setPlatform(platform);
        entry.setCountValue(1);

        Map<String, Object> metadata = metadata();
        putIfNotNull(metadata, "deviceIdentifier", deviceIdentifier);
        putIfNotNull(metadata, "platform", platform);
        entry.setMetadataJson(toJson(metadata));
        return auditLogRepository.save(entry);
    }

    public AuditLogEntity logDailyUnlockTotal(Long userId, Long relationshipId, Instant dayReference, int totalUnlocks) {
        AuditLogEntity entry =
                baseEntry(userId, relationshipId, AuditEventType.DAILY_UNLOCK_TOTAL, requireEventTime(dayReference));
        int safeTotal = Math.max(totalUnlocks, 0);
        entry.setCountValue(safeTotal);

        Map<String, Object> metadata = metadata();
        metadata.put("aggregation", "daily_unlock_total");
        metadata.put("reportedCount", safeTotal);
        entry.setMetadataJson(toJson(metadata));
        return auditLogRepository.save(entry);
    }

    public AuditLogEntity logAppUsage(
            Long userId,
            Long relationshipId,
            String applicationId,
            long durationSeconds,
            Instant startedAt,
            Instant endedAt,
            String platform) {

        AuditLogEntity entry =
                baseEntry(userId, relationshipId, AuditEventType.APP_USAGE, requireEventTime(startedAt));
        entry.setApplicationId(applicationId);
        entry.setPlatform(platform);
        long safeDuration = Math.max(durationSeconds, 0L);
        entry.setDurationSeconds(safeDuration);

        Map<String, Object> metadata = metadata();
        putIfNotNull(metadata, "applicationId", applicationId);
        putIfNotNull(metadata, "startedAt", startedAt != null ? startedAt.toString() : null);
        putIfNotNull(metadata, "endedAt", endedAt != null ? endedAt.toString() : null);
        metadata.put("durationSeconds", safeDuration);
        putIfNotNull(metadata, "platform", platform);
        entry.setMetadataJson(toJson(metadata));
        return auditLogRepository.save(entry);
    }

    public AuditLogEntity logMonitoringView(
            Long viewerUserId, Long relationshipId, Long targetUserId, Instant viewedAt, String viewType) {
        AuditLogEntity entry =
                baseEntry(viewerUserId, relationshipId, AuditEventType.MONITORING_VIEW, requireEventTime(viewedAt));
        entry.setTargetUserId(targetUserId);
        entry.setCountValue(1);

        Map<String, Object> metadata = metadata();
        putIfNotNull(metadata, "viewType", viewType);
        putIfNotNull(metadata, "targetUserId", targetUserId);
        entry.setMetadataJson(toJson(metadata));
        return auditLogRepository.save(entry);
    }

    public AuditLogEntity logSensitiveAction(
            Long userId, Long relationshipId, String actionType, Instant occurredAt, String detail) {
        AuditLogEntity entry =
                baseEntry(userId, relationshipId, AuditEventType.SENSITIVE_ACTION, requireEventTime(occurredAt));
        entry.setCountValue(1);

        Map<String, Object> metadata = metadata();
        putIfNotNull(metadata, "action", actionType);
        putIfNotNull(metadata, "detail", detail);
        entry.setMetadataJson(toJson(metadata));
        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> fetchLogs(
            Long userId, Instant from, Instant to, Collection<AuditEventType> eventTypes) {
        Long safeUserId = Objects.requireNonNull(userId, "userId is required");
        Instant safeFrom = Objects.requireNonNull(from, "from is required");
        Instant safeTo = Objects.requireNonNull(to, "to is required");
        if (eventTypes == null || eventTypes.isEmpty()) {
            return auditLogRepository.findByUserIdAndEventTimeBetweenOrderByEventTimeAsc(safeUserId, safeFrom, safeTo);
        }
        return auditLogRepository.findByUserIdAndEventTimeBetweenAndEventTypeInOrderByEventTimeAsc(
                safeUserId, safeFrom, safeTo, eventTypes);
    }

    private AuditLogEntity baseEntry(
            Long userId, Long relationshipId, AuditEventType eventType, Instant eventTime) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setUserId(Objects.requireNonNull(userId, "userId is required"));
        entry.setRelationshipId(relationshipId);
        entry.setEventType(Objects.requireNonNull(eventType, "eventType is required"));
        entry.setEventTime(eventTime);
        entry.setEventDate(toEventDate(eventTime));
        entry.setMetadataJson("{}");
        return entry;
    }

    private LocalDate toEventDate(Instant eventTime) {
        return eventTime.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private Instant requireEventTime(Instant eventTime) {
        return Objects.requireNonNull(eventTime, "eventTime is required");
    }

    private Map<String, Object> metadata() {
        return new LinkedHashMap<>();
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit metadata", exception);
        }
    }
}