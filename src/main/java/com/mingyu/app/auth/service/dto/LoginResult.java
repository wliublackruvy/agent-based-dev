// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.service.dto;

public record LoginResult(String token,
                          long expiresInSeconds,
                          Long userId,
                          String phoneMasked) {
}
