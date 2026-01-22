// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.service.dto;

public record LoginCommand(String phone,
                           String smsCode,
                           String scene,
                           String deviceId,
                           String deviceModel,
                           String platform) {
}
