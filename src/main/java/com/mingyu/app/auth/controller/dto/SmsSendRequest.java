// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsSendRequest(
        @NotBlank
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
        String phone,
        @NotBlank
        String scene
) {
}
