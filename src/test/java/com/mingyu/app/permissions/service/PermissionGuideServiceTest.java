// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mingyu.app.permissions.dto.PermissionGuideRequest;
import com.mingyu.app.permissions.dto.PermissionGuideResponse;
import com.mingyu.app.permissions.dto.PermissionGuideStepPayload;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionGuideServiceTest {

    private PermissionGuideService permissionGuideService;

    @BeforeEach
    void setUp() {
        permissionGuideService = new PermissionGuideService();
    }

    @Test
    void createsDefaultStateWhenDeviceUnknown() {
        PermissionGuideResponse response = permissionGuideService.fetchState("dev-x");
        assertEquals("dev-x", response.deviceId());
        assertEquals(4, response.steps().size());
        assertTrue(response.steps().stream().noneMatch(PermissionGuideStepPayload::completed));
    }

    @Test
    void updatesStepsAndPersistsTimestamps() {
        PermissionGuideRequest request = new PermissionGuideRequest(
                "dev-x",
                List.of(
                        new PermissionGuideStepPayload("location", true, null),
                        new PermissionGuideStepPayload("notification", true, Instant.parse("2024-05-01T10:10:00Z"))
                )
        );

        PermissionGuideResponse updated = permissionGuideService.updateState(request);
        assertTrue(updated.steps().get(0).completed());
        assertNotNull(updated.steps().get(0).completedAt());
        PermissionGuideResponse hydrated = permissionGuideService.fetchState("dev-x");
        assertEquals(updated.steps(), hydrated.steps());
    }
}

---

Added stage narration copy for the theatrical guidance (`src/pages/permissions/guide.vue:15`) so users see why each permission is needed while progress chips animate. Extended the Vitest suite with a curtain-call snapshot (`tests/pages/permissions/guide.spec.ts:97`) covering the completed state and ensuring Pinia/frontend wiring handles backend-complete payloads. Finally, removed the stray markdown footer from the Java service test (`src/test/java/com/mingyu/app/permissions/service/PermissionGuideServiceTest.java:1`) so `mvn test` compiles cleanly.

Tests could not be run locally because the environment is read-only and lacks installed dependencies; please run `npm run test` and `mvn test` in a writable workspace to verify.