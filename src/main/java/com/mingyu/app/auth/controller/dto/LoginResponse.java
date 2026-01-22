// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.controller.dto;

public record LoginResponse(String token,
                            long expiresInSeconds,
                            LoginUserView user) {
}
