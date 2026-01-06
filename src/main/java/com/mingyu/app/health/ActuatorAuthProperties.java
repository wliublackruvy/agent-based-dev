package com.mingyu.app.health;

// Implements System

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Externalizes the basic-auth credentials used to secure actuator endpoints. */
@ConfigurationProperties(prefix = "monitoring.health")
@Validated
public class ActuatorAuthProperties {

    @NotBlank
    private String username = "health-probe";

    @NotBlank
    private String password = "health-secret";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}