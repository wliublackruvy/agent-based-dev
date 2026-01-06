package com.mingyu.app;

// Implements REQ-1.1
// Implements 6.开发者核心逻辑

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.mingyu.app", "com.example.auth"})
@EnableScheduling
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}