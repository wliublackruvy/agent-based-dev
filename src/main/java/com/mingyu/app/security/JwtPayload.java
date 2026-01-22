// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.security;

public record JwtPayload(Long userId, String deviceId, int tokenVersion) {
}
