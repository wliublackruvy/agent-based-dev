// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
        String phone,
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
        String smsCode,
        @NotBlank
        String deviceId,
        @NotBlank
        String deviceModel,
        @NotBlank
        String platform
) {
}
