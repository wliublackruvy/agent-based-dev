// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.dto;

import java.time.Instant;
import java.util.List;

public record PermissionGuideResponse(
        String deviceId,
        List<PermissionGuideStepPayload> steps,
        Instant updatedAt
) {
}