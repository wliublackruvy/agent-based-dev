package com.mingyu.app.integration;

import com.mingyu.app.AgentBasedDevApplication;
import com.mingyu.app.auth.controller.dto.LoginRequest;
import com.mingyu.app.auth.domain.VerificationCodeStore;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
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
@DisplayName("关系模块集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
public class RelationIntegrationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private VerificationCodeStore verificationCodeStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:relationdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    private static String userAToken;
    private static String userBToken;
    private static String userCToken;
    private static String bindCode;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.basePath = "/api/v1";

        if (userAToken == null) {
            userAToken = loginAndGetToken("13900000001", "DEV_A");
            userBToken = loginAndGetToken("13900000002", "DEV_B");
            userCToken = loginAndGetToken("13900000003", "DEV_C");
        }
    }

    private String loginAndGetToken(String phone, String deviceId) {
        String code = "123456";
        verificationCodeStore.saveCode(phone, "LOGIN", code, Duration.ofMinutes(5));
        
        Response response = given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest(phone, code, deviceId, "iPhone", "IOS"))
        .when()
                .post("/auth/login");
        
        return response.path("data.token");
    }

    @Nested
    @DisplayName("绑定码生成与使用")
    @Order(1)
    class BindCodeFlow {

        @Test
        @DisplayName("正例 - 用户A生成绑定码")
        @Order(1)
        void generateBindCode_Success() {
            Response response = given()
                    .header("Authorization", "Bearer " + userAToken)
            .when()
                    .post("/relation/bind-code");

            response.then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data.bindCode", matchesPattern("[A-Z0-9]{6}"));

            bindCode = response.path("data.bindCode");
        }

        @Test
        @DisplayName("正例 - 用户B使用绑定码与A建立关系")
        @Order(2)
        void bindWithCode_Success() {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("bindCode", bindCode);

            given()
                    .header("Authorization", "Bearer " + userBToken)
                    .contentType(ContentType.JSON)
                    .body(requestBody)
            .when()
                    .post("/relation/bind")
            .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("反例 - 绑定码无效")
        void bindWithCode_InvalidCode() {
            given()
                    .header("Authorization", "Bearer " + userBToken)
                    .contentType(ContentType.JSON)
                    .body(Map.of("bindCode", "999999"))
            .when()
                    .post("/relation/bind")
            .then()
                    .statusCode(409)
                    .body("code", equalTo(40902));
        }
    }

    @Nested
    @DisplayName("双方确认解绑流程")
    @Order(2)
    class UnbindFlow {

        @Test
        @DisplayName("正例 - 用户A发起解绑请求")
        @Order(1)
        void requestUnbind_Success() {
            given()
                    .header("Authorization", "Bearer " + userAToken)
                    .contentType(ContentType.JSON)
                    .body(Map.of("reason", "USER_REQUEST"))
            .when()
                    .post("/relation/unbind/request")
            .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("反例 - 非关系成员尝试确认解绑")
        void confirmUnbind_NotAuthorized() {
            given()
                    .header("Authorization", "Bearer " + userCToken)
                    .contentType(ContentType.JSON)
                    .body(Map.of("confirm", true))
            .when()
                    .post("/relation/unbind/confirm")
            .then()
                    .statusCode(403)
                    .body("code", equalTo(40300));
        }
    }
}