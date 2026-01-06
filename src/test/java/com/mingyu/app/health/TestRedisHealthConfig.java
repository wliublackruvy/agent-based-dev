package com.mingyu.app.health;

// Implements System

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Provides a deterministic Redis health indicator for tests so we do not need a
 * live Redis server.
 */
@Configuration
@Profile("test")
class TestRedisHealthConfig {

    @Bean(name = "redis")
    HealthIndicator redisHealthIndicator() {
        return () -> Health.up().withDetail("mock", true).build();
    }
}