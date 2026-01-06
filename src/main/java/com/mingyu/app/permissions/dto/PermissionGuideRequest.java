// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PermissionGuideRequest(
        @NotBlank String deviceId,
        @NotEmpty List<@Valid PermissionGuideStepPayload> steps
) {
}