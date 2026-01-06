// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.service;

import com.mingyu.app.permissions.dto.PermissionGuideRequest;
import com.mingyu.app.permissions.dto.PermissionGuideResponse;
import com.mingyu.app.permissions.dto.PermissionGuideStepPayload;
import com.mingyu.app.permissions.model.PermissionGuideAggregate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionGuideService {

    private static final List<String> ORDERED_STEP_IDS =
            List.of("location", "notification", "autostart", "usage");

    private final ConcurrentMap<String, PermissionGuideAggregate> stateStore =
            new ConcurrentHashMap<>();

    public PermissionGuideResponse fetchState(String deviceId) {
        String normalized = normalizeDeviceId(deviceId);
        PermissionGuideAggregate aggregate = getOrCreate(normalized);
        return toResponse(normalized, aggregate);
    }

    public PermissionGuideResponse updateState(PermissionGuideRequest request) {
        String normalized = normalizeDeviceId(request.deviceId());
        PermissionGuideAggregate aggregate = getOrCreate(normalized);
        Map<String, PermissionGuideStepPayload> steps = aggregate.getSteps();
        for (PermissionGuideStepPayload incoming : request.steps()) {
            Instant completedAt = incoming.completed()
                    ? (incoming.completedAt() != null ? incoming.completedAt() : Instant.now())
                    : null;
            steps.put(
                    incoming.id(),
                    new PermissionGuideStepPayload(incoming.id(), incoming.completed(), completedAt)
            );
        }
        ensureAllSteps(steps);
        aggregate.setUpdatedAt(Instant.now());
        return toResponse(normalized, aggregate);
    }

    private PermissionGuideAggregate getOrCreate(String deviceId) {
        return stateStore.computeIfAbsent(deviceId, key -> buildDefaultAggregate());
    }

    private PermissionGuideAggregate buildDefaultAggregate() {
        Map<String, PermissionGuideStepPayload> defaults = new LinkedHashMap<>();
        ORDERED_STEP_IDS.forEach(id -> defaults.put(id, new PermissionGuideStepPayload(id, false, null)));
        return new PermissionGuideAggregate(defaults, Instant.now());
    }

    private void ensureAllSteps(Map<String, PermissionGuideStepPayload> steps) {
        ORDERED_STEP_IDS.forEach(id ->
                steps.computeIfAbsent(id, key -> new PermissionGuideStepPayload(key, false, null)));
    }

    private PermissionGuideResponse toResponse(String deviceId, PermissionGuideAggregate aggregate) {
        List<PermissionGuideStepPayload> orderedSteps = ORDERED_STEP_IDS.stream()
                .map(id -> aggregate.getSteps().getOrDefault(id, new PermissionGuideStepPayload(id, false, null)))
                .collect(Collectors.toList());
        return new PermissionGuideResponse(deviceId, orderedSteps, aggregate.getUpdatedAt());
    }

    private String normalizeDeviceId(String deviceId) {
        return Objects.requireNonNull(deviceId, "deviceId").trim();
    }
}