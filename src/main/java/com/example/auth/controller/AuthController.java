package com.example.auth.controller;

// Implements REQ-1.1

import com.example.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/request-otp")
    public ResponseEntity<MessageResponse> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        authService.requestOtp(request.getPhone());
        return ResponseEntity.ok(new MessageResponse("OTP dispatched"));
    }

    @PostMapping("/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        String jwt = authService.verifyOtp(request.getPhone(), request.getOtp(), request.getDeviceId());
        return ResponseEntity.ok(new TokenResponse(jwt, request.getDeviceId()));
    }

    public static class RequestOtpRequest {
        @NotBlank
        private String phone;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    public static class VerifyOtpRequest {
        @NotBlank
        private String phone;

        @NotBlank
        private String otp;

        @NotBlank
        private String deviceId;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getOtp() {
            return otp;
        }

        public void setOtp(String otp) {
            this.otp = otp;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
    }

    public static class TokenResponse {
        private final String token;
        private final String deviceId;

        public TokenResponse(String token, String deviceId) {
            this.token = token;
            this.deviceId = deviceId;
        }

        public String getToken() {
            return token;
        }

        public String getDeviceId() {
            return deviceId;
        }
    }

    public static class MessageResponse {
        private final String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}