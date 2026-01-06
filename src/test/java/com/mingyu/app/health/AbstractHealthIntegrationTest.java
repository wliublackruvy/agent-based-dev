package com.mingyu.app.health;

// Implements System

import com.mingyu.app.AuthApplication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spins up the real Spring context with in-memory DB and mocked Redis health so
 * we can verify health endpoint security + behavior end to end.
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractHealthIntegrationTest {

    protected static final String HEALTH_USER = "test-health";
    protected static final String HEALTH_PASSWORD = "test-secret";

    private static final String REDIS_AUTOCONFIG_EXCLUDES =
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration";

    @DynamicPropertySource
    static void registerHealthTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:health;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "sa");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
        registry.add("monitoring.health.username", () -> HEALTH_USER);
        registry.add("monitoring.health.password", () -> HEALTH_PASSWORD);
        registry.add("spring.autoconfigure.exclude", () -> REDIS_AUTOCONFIG_EXCLUDES);
    }

    protected final String basicAuthHeader() {
        byte[] credentials = (HEALTH_USER + ":" + HEALTH_PASSWORD).getBytes(StandardCharsets.UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(credentials);
    }

    protected final HttpHeaders actuatorAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader());
        return headers;
    }
}