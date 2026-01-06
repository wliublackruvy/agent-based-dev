package com.mingyu.app.health;

// Implements System

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack test validating basic auth is required and the health payload
 * contains MySQL + Redis status when authenticated.
 */
class HealthSecurityIntegrationTest extends AbstractHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedHealthRequestReturnsClusterStatus() throws Exception {
        mockMvc.perform(get("/health").headers(actuatorAuthHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }
}