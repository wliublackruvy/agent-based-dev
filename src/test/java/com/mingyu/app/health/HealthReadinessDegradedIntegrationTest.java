package com.mingyu.app.health;

// Implements System

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that downstream dependency failures surface as HTTP 503 to Docker
 * probes and expose the failing component details.
 */
class HealthReadinessDegradedIntegrationTest extends AbstractHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessFailureReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/health").headers(actuatorAuthHeaders()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("DOWN"));
    }

    @TestConfiguration
    static class DegradedDbHealthConfig {

        @Bean(name = "db")
        @Primary
        HealthIndicator failingDbHealthIndicator() {
            return () -> Health.down().withDetail("reason", "simulated-outage").build();
        }
    }
}

- Added a shared `HttpHeaders` builder alongside the existing Base64 helper so every integration test can reuse the same actuator credentials without duplicating header wiring (`src/test/java/com/mingyu/app/health/AbstractHealthIntegrationTest.java:1`).
- Updated both security and degraded-readiness suites to consume the shared header helper, removing the duplicated `HttpHeaders` constant usage and the stray markdown that was breaking compilation (`src/test/java/com/mingyu/app/health/HealthSecurityIntegrationTest.java:1`, `src/test/java/com/mingyu/app/health/HealthReadinessDegradedIntegrationTest.java:1`).

Tests were not run locally because the workspace is mounted read-only; please execute `mvn test` once you have a writable checkout.