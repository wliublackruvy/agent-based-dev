package com.mingyu.app.relationship.model;

// Implements 1.账号与关系管理

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
import java.util.Objects;

@Entity
@Table(
        name = "relationships",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_relationship_binding_code", columnNames = "binding_code")
        }
)
@TableName("relationships")
public class RelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "initiator_user_id", nullable = false)
    @TableField("initiator_user_id")
    private Long initiatorUserId;

    @Column(name = "partner_user_id", nullable = false)
    @TableField("partner_user_id")
    private Long partnerUserId;

    @TableField(exist = false)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_user_id", insertable = false, updatable = false)
    private UserEntity initiator;

    @TableField(exist = false)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_user_id", insertable = false, updatable = false)
    private UserEntity partner;

    @Column(name = "binding_code", nullable = false, length = 6)
    @TableField("binding_code")
    private String bindingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @TableField("status")
    private RelationshipStatus status = RelationshipStatus.PENDING;

    @Column(name = "requested_device_identifier", nullable = false, length = 64)
    @TableField("requested_device_identifier")
    private String requestedDeviceIdentifier;

    @Column(name = "partner_device_identifier", length = 64)
    @TableField("partner_device_identifier")
    private String partnerDeviceIdentifier;

    @Column(name = "requested_at", nullable = false)
    @TableField("requested_at")
    private Instant requestedAt;

    @Column(name = "confirmed_at")
    @TableField("confirmed_at")
    private Instant confirmedAt;

    @Column(name = "terminated_at")
    @TableField("terminated_at")
    private Instant terminatedAt;

    @Column(name = "termination_reason", length = 255)
    @TableField("termination_reason")
    private String terminationReason;

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
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (status == null) {
            status = RelationshipStatus.PENDING;
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markActive(String partnerDeviceIdentifier, Instant confirmedAt) {
        Instant effectiveConfirmedAt = confirmedAt != null ? confirmedAt : Instant.now();
        this.status = RelationshipStatus.ACTIVE;
        this.partnerDeviceIdentifier = partnerDeviceIdentifier;
        this.confirmedAt = effectiveConfirmedAt;
        this.terminatedAt = null;
        this.terminationReason = null;
    }

    public void terminate(String reason, Instant terminatedAt) {
        Instant effectiveTerminatedAt = terminatedAt != null ? terminatedAt : Instant.now();
        this.status = RelationshipStatus.TERMINATED;
        this.terminatedAt = effectiveTerminatedAt;
        this.terminationReason = reason;
    }

    public boolean isActive() {
        return RelationshipStatus.ACTIVE.equals(this.status);
    }

    public boolean involvesUser(Long userId) {
        return Objects.equals(initiatorUserId, userId) || Objects.equals(partnerUserId, userId);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInitiatorUserId() {
        return initiatorUserId;
    }

    public void setInitiatorUserId(Long initiatorUserId) {
        this.initiatorUserId = initiatorUserId;
    }

    public Long getPartnerUserId() {
        return partnerUserId;
    }

    public void setPartnerUserId(Long partnerUserId) {
        this.partnerUserId = partnerUserId;
    }

    public UserEntity getInitiator() {
        return initiator;
    }

    public void setInitiator(UserEntity initiator) {
        this.initiator = initiator;
        this.initiatorUserId = initiator != null ? initiator.getId() : null;
    }

    public UserEntity getPartner() {
        return partner;
    }

    public void setPartner(UserEntity partner) {
        this.partner = partner;
        this.partnerUserId = partner != null ? partner.getId() : null;
    }

    public String getBindingCode() {
        return bindingCode;
    }

    public void setBindingCode(String bindingCode) {
        this.bindingCode = bindingCode;
    }

    public RelationshipStatus getStatus() {
        return status;
    }

    public void setStatus(RelationshipStatus status) {
        this.status = status;
    }

    public String getRequestedDeviceIdentifier() {
        return requestedDeviceIdentifier;
    }

    public void setRequestedDeviceIdentifier(String requestedDeviceIdentifier) {
        this.requestedDeviceIdentifier = requestedDeviceIdentifier;
    }

    public String getPartnerDeviceIdentifier() {
        return partnerDeviceIdentifier;
    }

    public void setPartnerDeviceIdentifier(String partnerDeviceIdentifier) {
        this.partnerDeviceIdentifier = partnerDeviceIdentifier;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(Instant terminatedAt) {
        this.terminatedAt = terminatedAt;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
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