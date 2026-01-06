package com.mingyu.app.relationship.model;

// Implements 1.账号与关系管理

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone_number"),
                @UniqueConstraint(name = "uk_users_binding_code", columnNames = "binding_code")
        }
)
@TableName("users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20, unique = true)
    @TableField("phone_number")
    private String phoneNumber;

    @Column(name = "display_name", nullable = false, length = 64)
    @TableField("display_name")
    private String displayName;

    @Column(name = "country_code", length = 6)
    @TableField("country_code")
    private String countryCode;

    @Column(name = "binding_code", length = 6, unique = true)
    @TableField("binding_code")
    private String bindingCode;

    @Column(name = "binding_code_expires_at")
    @TableField("binding_code_expires_at")
    private Instant bindingCodeExpiresAt;

    @Column(name = "active", nullable = false)
    @TableField("active")
    private boolean active = true;

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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getBindingCode() {
        return bindingCode;
    }

    public void setBindingCode(String bindingCode) {
        this.bindingCode = bindingCode;
    }

    public Instant getBindingCodeExpiresAt() {
        return bindingCodeExpiresAt;
    }

    public void setBindingCodeExpiresAt(Instant bindingCodeExpiresAt) {
        this.bindingCodeExpiresAt = bindingCodeExpiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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