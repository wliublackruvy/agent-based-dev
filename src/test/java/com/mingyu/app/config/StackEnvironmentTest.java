package com.mingyu.app.config;

// Implements 1.账号与关系管理

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StackEnvironmentTest {

    @Test
    void databaseSettingsRequireVariables() {
        StackEnvironment environment = new StackEnvironment(
                Map.of(
                        "MYSQL_DATABASE", "mingyu_app",
                        "MYSQL_USER", "mingyu_user",
                        "MYSQL_PASSWORD", "mingyu_password",
                        "MYSQL_PORT", "3307",
                        "REDIS_PORT", "6380",
                        "APP_PROFILE", "local"
                )
        );

        StackEnvironment.DatabaseSettings settings = environment.database();

        assertEquals("mingyu_app", settings.name());
        assertEquals("mingyu_user", settings.user());
        assertEquals("mingyu_password", settings.password());
        assertEquals(3307, settings.port());
        assertEquals(6380, environment.redisPort());
        assertEquals("local", environment.profile());
    }

    @Test
    void defaultsAppliedWhenOptionalVariablesMissing() {
        StackEnvironment environment = new StackEnvironment(
                Map.of(
                        "MYSQL_DATABASE", "mingyu_app",
                        "MYSQL_USER", "mingyu_user",
                        "MYSQL_PASSWORD", "mingyu_password"
                )
        );

        StackEnvironment.DatabaseSettings settings = environment.database();

        assertEquals(3306, settings.port());
        assertEquals(6379, environment.redisPort());
        assertEquals("local", environment.profile());
    }

    @Test
    void invalidPortThrowsHelpfulException() {
        StackEnvironment environment = new StackEnvironment(
                Map.of(
                        "MYSQL_DATABASE", "mingyu_app",
                        "MYSQL_USER", "mingyu_user",
                        "MYSQL_PASSWORD", "mingyu_password",
                        "MYSQL_PORT", "invalid"
                )
        );

        assertThrows(IllegalStateException.class, environment::database);
    }
}