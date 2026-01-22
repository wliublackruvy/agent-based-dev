// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.domain;

import java.time.Duration;
import java.util.Optional;

public interface VerificationCodeStore {

    void saveCode(String phone, String scene, String code, Duration ttl);

    boolean validateAndConsume(String phone, String scene, String code);

    default Optional<String> peekCode(String phone, String scene) {
        return Optional.empty();
    }
}
