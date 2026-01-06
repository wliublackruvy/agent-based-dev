package com.mingyu.app.audit.model;

// Implements 4.会员审计功能

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "audit_log_entries",
        indexes = {
                @Index(name = "idx_audit_user_time", columnList = "user_id,event_time"),
                @Index(name = "idx_audit_type_time", columnList = "event_type,event_time"),
                @Index(name = "idx_audit_relationship_time", columnList = "relationship_id,event_time")
        }
)
@TableName("audit_log_entries")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @TableField("user_id")
    private Long userId;

    @Column(name = "target_user_id")
    @TableField("target_user_id")
    private Long targetUserId;

    @Column(name = "relationship_id")
    @TableField("relationship_id")
    private Long relationshipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    @TableField("event_type")
    private AuditEventType eventType;

    @Column(name = "event_time", nullable = false)
    @TableField("event_time")
    private Instant eventTime;

    @Column(name = "event_date", nullable = false)
    @TableField("event_date")
    private LocalDate eventDate;

    @Column(name = "device_identifier", length = 64)
    @TableField("device_identifier")
    private String deviceIdentifier;

    @Column(name = "application_id", length = 128)
    @TableField("application_id")
    private String applicationId;

    @Column(name = "platform", length = 32)
    @TableField("platform")
    private String platform;

    @Column(name = "duration_seconds")
    @TableField("duration_seconds")
    private Long durationSeconds;

    @Column(name = "count_value")
    @TableField("count_value")
    private Integer countValue;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    @TableField("metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    @TableField("created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @TableField("updated_at")
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getRelationshipId() {
        return relationshipId;
    }

    public void setRelationshipId(Long relationshipId) {
        this.relationshipId = relationshipId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getCountValue() {
        return countValue;
    }

    public void setCountValue(Integer countValue) {
        this.countValue = countValue;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}