package com.mingyu.app.health;

// Implements System

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom /health endpoint that proxies the readiness group so Docker Compose
 * probes can receive a simple UP/503 contract without exposing the entire
 * actuator surface.
 */
@RestController
public class HealthProbeController {

    private static final String READINESS_GROUP = "readiness";

    private final HealthEndpoint healthEndpoint;

    public HealthProbeController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HealthComponent> health() {
        HealthComponent readiness = snapshotReadiness();
        Status status = readiness != null ? readiness.getStatus() : null;
        return ResponseEntity.status(mapStatus(status)).body(readiness);
    }

    @RequestMapping(value = "/health", method = RequestMethod.HEAD)
    public ResponseEntity<Void> healthHead() {
        HealthComponent readiness = snapshotReadiness();
        Status status = readiness != null ? readiness.getStatus() : null;
        return ResponseEntity.status(mapStatus(status)).build();
    }

    private HealthComponent snapshotReadiness() {
        try {
            HealthComponent readiness = healthEndpoint.healthForPath(READINESS_GROUP);
            if (readiness == null) {
                readiness = healthEndpoint.health();
            }
            return readiness != null ? readiness : Health.unknown().build();
        } catch (RuntimeException ex) {
            return Health.down(ex).build();
        }
    }

    private HttpStatus mapStatus(Status status) {
        return status != null && Status.UP.equals(status)
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
    }
}