package com.example.auth.service;

// Implements REQ-1.1

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

public interface OtpGenerator {
    String generate();
}

@Component
class DefaultOtpGenerator implements OtpGenerator {

    private static final int OTP_LENGTH = 6;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }
}