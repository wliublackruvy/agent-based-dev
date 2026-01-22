package com.mingyu.app.integration;

import com.mingyu.app.AgentBasedDevApplication;
import com.mingyu.app.auth.controller.dto.LoginRequest;
import com.mingyu.app.auth.domain.VerificationCodeStore;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = AgentBasedDevApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("认证模块集成测试")
public class AuthIntegrationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private VerificationCodeStore verificationCodeStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.basePath = "/api/v1";
    }

    @Nested
    @DisplayName("短信验证码发送")
    class SendSmsCode {
        @Test
        @DisplayName("正例 - 发送成功")
        void sendSmsCode_Success() {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("phone", "13800000000");
            requestBody.put("scene", "LOGIN");

            given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
            .when()
                    .post("/auth/sms/send")
            .then()
                    .statusCode(200)
                    .body("code", is(0));
        }
    }

    @Nested
    @DisplayName("登录与设备绑定")
    class LoginAndDeviceBinding {

        @Test
        @DisplayName("新用户登录成功并绑定设备")
        void login_NewUser_Success() {
            String phone = "13800000001";
            String code = "123456";
            verificationCodeStore.saveCode(phone, "LOGIN", code, Duration.ofMinutes(5));

            given()
                    .contentType(ContentType.JSON)
                    .body(new LoginRequest(phone, code, "DEV001", "Pixel 6", "ANDROID"))
            .when()
                    .post("/auth/login")
            .then()
                    .statusCode(200)
                    .body("code", is(0))
                    .body("data.token", notNullValue());
        }

        @Test
        @DisplayName("老用户换设备登录，使旧设备Token失效")
        void login_ExistingUser_NewDevice_InvalidatesOldToken() {
            String phone = "13800000002";
            String code = "123456";
            
            verificationCodeStore.saveCode(phone, "LOGIN", code, Duration.ofMinutes(5));
            String oldToken = given()
                    .contentType(ContentType.JSON)
                    .body(new LoginRequest(phone, code, "DEV_OLD", "iPhone 12", "IOS"))
            .when()
                    .post("/auth/login")
            .then()
                    .extract().path("data.token");

            verificationCodeStore.saveCode(phone, "LOGIN", code, Duration.ofMinutes(5));
            given()
                    .contentType(ContentType.JSON)
                    .body(new LoginRequest(phone, code, "DEV_NEW", "iPhone 15", "IOS"))
            .when()
                    .post("/auth/login")
            .then()
                    .statusCode(200);

            given()
                    .header("Authorization", "Bearer " + oldToken)
            .when()
                    .get("/relation/bind-code")
            .then()
                    .statusCode(401)
                    .body("code", is(40101));
        }
    }
}