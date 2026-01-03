package com.example.auth.exception;

// Implements REQ-1.1

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException(String message) {
        super(message);
    }
}