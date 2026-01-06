package com.mingyu.app.health;

// Implements System

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Ensures the /health proxy returns the right snapshot + HTTP status. */
@ExtendWith(MockitoExtension.class)
class HealthProbeControllerTest {

    @Mock
    private HealthEndpoint healthEndpoint;

    @InjectMocks
    private HealthProbeController controller;

    @Test
    void returnsReadinessSnapshotWhenAvailable() {
        Health readiness = Health.up().withDetail("probe", "readiness").build();
        when(healthEndpoint.healthForPath("readiness")).thenReturn(readiness);

        ResponseEntity<HealthComponent> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(readiness);
    }

    @Test
    void readinessDownPropagatesServiceUnavailableStatus() {
        Health readinessDown = Health.down().withDetail("dependency", "mysql").build();
        when(healthEndpoint.healthForPath("readiness")).thenReturn(readinessDown);

        ResponseEntity<HealthComponent> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(readinessDown);
    }

    @Test
    void fallsBackToAggregateSnapshotWhenReadinessUnavailable() {
        when(healthEndpoint.healthForPath("readiness")).thenThrow(new IllegalStateException("missing"));
        Health aggregate = Health.down().withDetail("dependency", "db").build();
        when(healthEndpoint.health()).thenReturn(aggregate);

        ResponseEntity<HealthComponent> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(aggregate);
    }

    @Test
    void headRequestReflectsStatusAndDoesNotIncludeBody() {
        Health readinessDown = Health.down().withDetail("dependency", "redis").build();
        when(healthEndpoint.healthForPath("readiness")).thenReturn(readinessDown);

        ResponseEntity<Void> response = controller.healthHead();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNull();
    }
}