package com.mingyu.app.relationship.model;

// Implements 1.账号与关系管理
// Implements 6.开发者核心逻辑

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_devices_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_devices_identifier", columnNames = "device_identifier")
        }
)
@TableName("devices")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @TableField("user_id")
    private Long userId;

    @TableField(exist = false)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserEntity user;

    @Column(name = "device_identifier", nullable = false, length = 64)
    @TableField("device_identifier")
    private String deviceIdentifier;

    @Column(name = "platform", nullable = false, length = 32)
    @TableField("platform")
    private String platform;

    @Column(name = "model", length = 64)
    @TableField("model")
    private String model;

    @Column(name = "os_version", length = 32)
    @TableField("os_version")
    private String osVersion;

    @Column(name = "app_version", length = 32)
    @TableField("app_version")
    private String appVersion;

    @Column(name = "push_token", length = 128)
    @TableField("push_token")
    private String pushToken;

    @Column(name = "binding_reason", length = 128)
    @TableField("binding_reason")
    private String bindingReason;

    @Column(name = "active", nullable = false)
    @TableField("active")
    private boolean active = true;

    @Column(name = "bound_at", nullable = false)
    @TableField("bound_at")
    private Instant boundAt;

    @Column(name = "last_seen_at")
    @TableField("last_seen_at")
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "heartbeat_state", nullable = false, length = 16)
    @TableField("heartbeat_state")
    private DeviceHeartbeatState heartbeatState = DeviceHeartbeatState.HEALTHY;

    @Column(name = "revoked_at")
    @TableField("revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @TableField("created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @TableField("updated_at")
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (boundAt == null) {
            boundAt = now;
        }
        if (heartbeatState == null) {
            heartbeatState = DeviceHeartbeatState.HEALTHY;
        }
    }

    @PreUpdate
    private void preUpdate() {
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
        if (this.user != null && userId != null) {
            if (!userId.equals(this.user.getId())) {
                this.user = null;
            }
        }
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
        this.userId = user != null ? user.getId() : null;
    }

    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getPushToken() {
        return pushToken;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    public String getBindingReason() {
        return bindingReason;
    }

    public void setBindingReason(String bindingReason) {
        this.bindingReason = bindingReason;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(Instant boundAt) {
        this.boundAt = boundAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
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

    public DeviceHeartbeatState getHeartbeatState() {
        return heartbeatState;
    }

    public void setHeartbeatState(DeviceHeartbeatState heartbeatState) {
        this.heartbeatState = heartbeatState;
    }
}