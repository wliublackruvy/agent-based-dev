package com.example.auth.controller;

// Implements REQ-1.1
// Implements 1.账号与关系管理

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.service.AuthService;
import com.example.auth.service.OtpDeliveryClient;
import com.example.auth.service.OtpGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingyu.app.AuthApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "auth.jwt.secret=TestSecretKeyForJwtSignatures1234567890",
        "auth.token.ttl-seconds=3600",
        "auth.otp.ttl-seconds=300",
        "auth.otp.rate-limit-seconds=60"
})
@Import(AuthControllerTest.ClockTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private AuthService authService;

    @MockBean
    private OtpGenerator otpGenerator;

    @MockBean
    private OtpDeliveryClient otpDeliveryClient;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void resetClock() {
        mutableClock.reset();
    }

    @Test
    void requestOtpRateLimited() throws Exception {
        when(otpGenerator.generate()).thenReturn("123456");
        performOtpRequest("+15555555555").andExpect(status().isOk());
        performOtpRequest("+15555555555").andExpect(status().isTooManyRequests());
    }

    @Test
    void verifyOtpReturnsJwtBoundToDevice() throws Exception {
        when(otpGenerator.generate()).thenReturn("654321");
        performOtpRequest("+15551234567").andExpect(status().isOk());

        String token = extractToken(mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+15551234567\",\"otp\":\"654321\",\"deviceId\":\"device-1\"}"))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode payload = readTokenPayload(token);
        assertEquals("+15551234567", payload.get("sub").asText());
        assertEquals("device-1", payload.get("device_id").asText());
    }

    @Test
    void newDeviceRevokesOldTokens() throws Exception {
        when(otpGenerator.generate()).thenReturn("111111", "222222");

        performOtpRequest("+15550001111").andExpect(status().isOk());
        String firstToken = extractToken(mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+15550001111\",\"otp\":\"111111\",\"deviceId\":\"device-a\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mutableClock.plusSeconds(120);

        performOtpRequest("+15550001111").andExpect(status().isOk());
        String secondToken = extractToken(mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+15550001111\",\"otp\":\"222222\",\"deviceId\":\"device-b\"}"))
                .andExpect(status().isOk())
                .andReturn());

        assertTrue(authService.isTokenRevoked(firstToken));
        assertFalse(authService.isTokenRevoked(secondToken));
    }

    private ResultActions performOtpRequest(String phone) throws Exception {
        return mockMvc.perform(post("/auth/request-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}"));
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private JsonNode readTokenPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(payloadBytes);
    }

    @TestConfiguration
    static class ClockTestConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    static class MutableClock extends Clock {

        private final ZoneId zone;
        private final Instant startInstant;
        private Instant currentInstant;

        MutableClock(Instant startInstant, ZoneId zone) {
            this.zone = zone;
            this.startInstant = startInstant;
            this.currentInstant = startInstant;
        }

        void plusSeconds(long seconds) {
            currentInstant = currentInstant.plusSeconds(seconds);
        }

        void reset() {
            currentInstant = startInstant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}