package com.mingyu.app.common.error;

// Implements System

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.controller.AuthController;
import com.example.auth.service.AuthService;
import com.mingyu.app.logging.TraceLoggingFilter;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {AuthController.class, GlobalExceptionHandlerTest.QueryValidationController.class})
@Import({GlobalExceptionHandler.class, TraceLoggingFilter.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    void validationErrorsIncludeTraceId() throws Exception {
        mockMvc.perform(post("/auth/request-otp")
                        .header("X-Trace-Id", "trace-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").value("trace-validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void serverErrorsIncludeTraceId() throws Exception {
        when(authService.verifyOtp(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/auth/verify")
                        .header("X-Trace-Id", "trace-server")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+15550000000\",\"otp\":\"111111\",\"deviceId\":\"device-z\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.traceId").value("trace-server"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void bindValidationErrorsIncludeTraceId() throws Exception {
        mockMvc.perform(get("/diagnostic/bind")
                        .header("X-Trace-Id", "trace-bind")
                        .param("phone", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").value("trace-bind"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @RestController
    @RequestMapping("/diagnostic")
    static class QueryValidationController {

        @GetMapping("/bind")
        public String trigger(@Validated QueryRequest request) {
            return request.getPhone();
        }

        static class QueryRequest {
            @NotBlank
            private String phone;

            public String getPhone() {
                return phone;
            }

            public void setPhone(String phone) {
                this.phone = phone;
            }
        }
    }
}

All controllers now return the standardized `ApiErrorResponse` envelope with guaranteed `traceId`s (`src/main/java/com/mingyu/app/common/error/GlobalExceptionHandler.java:35`). Logging headers/fields are pushed into MDC via `TraceLoggingFilter` (`src/main/java/com/mingyu/app/logging/TraceLoggingFilter.java:19`), formatted by logback (`src/main/resources/logback-spring.xml:3`), and propagated to async tasks with the MDC-aware executor/decorator combo (`src/main/java/com/mingyu/app/logging/LoggingAsyncConfig.java:13`, `src/main/java/com/mingyu/app/logging/MdcTaskDecorator.java:10`). `GlobalExceptionHandlerTest` (src/test/java/com/mingyu/app/common/error/GlobalExceptionHandlerTest.java:28) exercises both validation and server-side exception paths, ensuring the JSON payload retains the supplied trace IDs. Tests not run here (read-only sandbox); please execute `mvn test` locally. Suggested next step: 1) run the full unit suite to confirm the handler and filter wiring (`mvn test`).