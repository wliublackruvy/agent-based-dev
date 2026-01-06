package com.mingyu.app.config;

// Implements 1.账号与关系管理

import java.util.Map;

public class StackEnvironment {

    public record DatabaseSettings(String name, String user, String password, int port) {
    }

    private final Map<String, String> environment;

    public StackEnvironment(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    public DatabaseSettings database() {
        return new DatabaseSettings(
                require("MYSQL_DATABASE"),
                require("MYSQL_USER"),
                require("MYSQL_PASSWORD"),
                parsePort(environment.getOrDefault("MYSQL_PORT", "3306"))
        );
    }

    public int redisPort() {
        return parsePort(environment.getOrDefault("REDIS_PORT", "6379"));
    }

    public String profile() {
        return environment.getOrDefault("APP_PROFILE", "local");
    }

    private String require(String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + key);
        }
        return value;
    }

    private int parsePort(String rawValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid port value: " + rawValue, exception);
        }
    }
}