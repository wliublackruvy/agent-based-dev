package com.example.auth.service;

// Implements REQ-1.1

import com.example.auth.exception.InvalidOtpException;
import com.example.auth.exception.RateLimitException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DEVICE_CLAIM = "device_id";

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
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
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
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        return Jwts.builder()
                .setSubject(phone)
                .claim(DEVICE_CLAIM, deviceId)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .signWith(jwtKey, SignatureAlgorithm.HS256)
                .compact();
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