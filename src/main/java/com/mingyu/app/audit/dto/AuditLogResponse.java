package com.mingyu.app.audit.dto;

// Implements 4.会员审计功能

import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import java.time.Instant;
import java.time.LocalDate;

public record AuditLogResponse(
        Long id,
        Long userId,
        Long targetUserId,
        Long relationshipId,
        AuditEventType eventType,
        Instant eventTime,
        LocalDate eventDate,
        String deviceIdentifier,
        String applicationId,
        Long durationSeconds,
        Integer countValue,
        String platform,
        String metadataJson
) {

    public static AuditLogResponse fromEntity(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getTargetUserId(),
                entity.getRelationshipId(),
                entity.getEventType(),
                entity.getEventTime(),
                entity.getEventDate(),
                entity.getDeviceIdentifier(),
                entity.getApplicationId(),
                entity.getDurationSeconds(),
                entity.getCountValue(),
                entity.getPlatform(),
                entity.getMetadataJson()
        );
    }
}