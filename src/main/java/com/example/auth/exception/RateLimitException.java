package com.example.auth.exception;

// Implements REQ-1.1

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}