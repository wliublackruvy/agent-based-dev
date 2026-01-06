package com.example.auth.service;

// Implements REQ-1.1
// Implements 1.账号与关系管理

import com.example.auth.exception.InvalidOtpException;
import com.example.auth.exception.RateLimitException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DEVICE_CLAIM = "device_id";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final OtpGenerator otpGenerator;
    private final OtpDeliveryClient otpDeliveryClient;
    private final Clock clock;
    private final Duration otpTtl;
    private final Duration rateLimitWindow;
    private final Duration tokenTtl;
    private final SecretKey jwtKey;

    private final Map<String, OtpRecord> otpStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> otpRequestAudit = new ConcurrentHashMap<>();
    private final Map<String, DeviceSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public AuthService(
            OtpGenerator otpGenerator,
            OtpDeliveryClient otpDeliveryClient,
            Clock clock,
            @Value("${auth.otp.ttl-seconds:300}") long otpTtlSeconds,
            @Value("${auth.otp.rate-limit-seconds:60}") long rateLimitSeconds,
            @Value("${auth.token.ttl-seconds:86400}") long tokenTtlSeconds,
            @Value("${auth.jwt.secret:ChangeMeToASecureSecretKeyChangeMe123456}") String jwtSecret) {
        this.otpGenerator = otpGenerator;
        this.otpDeliveryClient = otpDeliveryClient;
        this.clock = clock;
        this.otpTtl = Duration.ofSeconds(otpTtlSeconds);
        this.rateLimitWindow = Duration.ofSeconds(rateLimitSeconds);
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
        this.jwtKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public void requestOtp(String phone) {
        Instant now = clock.instant();
        Instant last = otpRequestAudit.get(phone);
        if (last != null && Duration.between(last, now).compareTo(rateLimitWindow) < 0) {
            throw new RateLimitException("OTP already issued within the last minute");
        }
        otpRequestAudit.put(phone, now);

        String otp = otpGenerator.generate();
        otpStore.put(phone, new OtpRecord(hashOtp(otp), now.plus(otpTtl)));
        otpDeliveryClient.deliver(phone, otp);
    }

    public String verifyOtp(String phone, String otp, String deviceId) {
        Instant now = clock.instant();
        OtpRecord record = otpStore.get(phone);
        if (record == null) {
            throw new InvalidOtpException("OTP not requested for phone");
        }
        if (now.isAfter(record.expiresAt())) {
            otpStore.remove(phone);
            throw new InvalidOtpException("OTP expired");
        }
        if (!record.hashedOtp().equals(hashOtp(otp))) {
            throw new InvalidOtpException("OTP mismatch");
        }
        otpStore.remove(phone);

        DeviceSession previousSession = activeSessions.get(phone);
        if (previousSession != null && !previousSession.deviceId().equals(deviceId)) {
            revokedTokens.add(previousSession.jwt());
        }

        String token = buildJwt(phone, deviceId);
        activeSessions.put(phone, new DeviceSession(deviceId, token));
        revokedTokens.remove(token);
        return token;
    }

    public boolean isTokenRevoked(String token) {
        return revokedTokens.contains(token);
    }

    private String buildJwt(String phone, String deviceId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", phone);
        payload.put(DEVICE_CLAIM, deviceId);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String headerEncoded = base64UrlEncode(HEADER_JSON);
            String payloadEncoded = base64UrlEncode(OBJECT_MAPPER.writeValueAsString(payload));
            String signature = sign(headerEncoded + "." + payloadEncoded);
            return headerEncoded + "." + payloadEncoded + "." + signature;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize JWT payload", ex);
        }
    }

    private static String base64UrlEncode(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(jwtKey);
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL_ENCODER.encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to calculate JWT signature", ex);
        }
    }

    private String hashOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private record OtpRecord(String hashedOtp, Instant expiresAt) {}

    private record DeviceSession(String deviceId, String jwt) {}
}