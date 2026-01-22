// Implements Task-BE-001 - Ref: PRD/Design
package com.mingyu.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtService {

    private final JwtProperties properties;
    private Key signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(JwtPayload payload) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("deviceId", payload.deviceId());
        claims.put("tokenVersion", payload.tokenVersion());
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(properties.getExpirationSeconds()));
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(payload.userId()))
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public JwtPayload parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        Long userId = Long.valueOf(claims.getSubject());
        String deviceId = claims.get("deviceId", String.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);
        return new JwtPayload(userId, deviceId, tokenVersion == null ? 0 : tokenVersion);
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }
}
