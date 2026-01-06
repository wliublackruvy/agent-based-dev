// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.model;

import com.mingyu.app.permissions.dto.PermissionGuideStepPayload;
import java.time.Instant;
import java.util.Map;

public class PermissionGuideAggregate {

    private final Map<String, PermissionGuideStepPayload> steps;
    private Instant updatedAt;

    public PermissionGuideAggregate(Map<String, PermissionGuideStepPayload> steps, Instant updatedAt) {
        this.steps = steps;
        this.updatedAt = updatedAt;
    }

    public Map<String, PermissionGuideStepPayload> getSteps() {
        return steps;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}