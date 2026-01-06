// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record PermissionGuideStepPayload(
        @NotBlank String id,
        boolean completed,
        Instant completedAt
) {
}