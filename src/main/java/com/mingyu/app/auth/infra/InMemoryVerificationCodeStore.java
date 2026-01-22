// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.auth.infra;

import com.mingyu.app.auth.domain.VerificationCodeStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private final ConcurrentHashMap<String, CodeHolder> store = new ConcurrentHashMap<>();

    @Override
    public void saveCode(String phone, String scene, String code, Duration ttl) {
        store.put(buildKey(phone, scene), new CodeHolder(code, Instant.now().plus(ttl)));
    }

    @Override
    public boolean validateAndConsume(String phone, String scene, String code) {
        String key = buildKey(phone, scene);
        CodeHolder holder = store.get(key);
        if (holder == null || holder.expireAt().isBefore(Instant.now())) {
            return false;
        }
        if (!holder.code().equals(code)) {
            return false;
        }
        store.remove(key);
        return true;
    }

    @Override
    public Optional<String> peekCode(String phone, String scene) {
        CodeHolder holder = store.get(buildKey(phone, scene));
        if (holder == null || holder.expireAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(holder.code());
    }

    private String buildKey(String phone, String scene) {
        return phone + ":" + scene;
    }

    private record CodeHolder(String code, Instant expireAt) {
    }
}
