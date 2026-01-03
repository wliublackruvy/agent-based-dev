package com.example.auth.service;

// Implements REQ-1.1

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

public interface OtpDeliveryClient {
    void deliver(String phone, String otp);
}

@Component
class LoggingOtpDeliveryClient implements OtpDeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpDeliveryClient.class);

    @Override
    public void deliver(String phone, String otp) {
        log.info("Dispatching OTP to phone {}", phone);
    }
}