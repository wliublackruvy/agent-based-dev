package com.mingyu.app.audit.controller;

// Implements System
// Implements 4.会员审计功能
// Implements 6.开发者核心逻辑

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import com.mingyu.app.audit.service.AuditLoggingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Health Hardening:
 * - /health and other actuator endpoints stay basic-auth only with form login/logout disabled.
 * - This keeps automated liveness/readiness probes simple and stateless.
 */
@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLoggingService auditLoggingService;

    @Test
    void returnsLogsFilteredByDatesAndTypes() throws Exception {
        AuditLogEntity unlock = new AuditLogEntity();
        unlock.setId(1L);
        unlock.setUserId(5L);
        unlock.setEventType(AuditEventType.DEVICE_UNLOCK);
        unlock.setEventTime(Instant.parse("2024-06-01T08:00:00Z"));
        unlock.setEventDate(LocalDate.parse("2024-06-01"));
        unlock.setDeviceIdentifier("dev-1");
        unlock.setCountValue(1);
        unlock.setMetadataJson("{\"deviceIdentifier\":\"dev-1\"}");

        AuditLogEntity monitoring = new AuditLogEntity();
        monitoring.setId(2L);
        monitoring.setUserId(5L);
        monitoring.setEventType(AuditEventType.MONITORING_VIEW);
        monitoring.setEventTime(Instant.parse("2024-06-01T09:00:00Z"));
        monitoring.setEventDate(LocalDate.parse("2024-06-01"));
        monitoring.setTargetUserId(7L);
        monitoring.setCountValue(1);
        monitoring.setMetadataJson("{\"viewType\":\"map\"}");

        when(auditLoggingService.fetchLogs(eq(5L), any(), any(), any()))
                .thenReturn(List.of(unlock, monitoring));

        Instant from = Instant.parse("2024-06-01T00:00:00Z");
        Instant to = Instant.parse("2024-06-02T00:00:00Z");

        mockMvc.perform(get("/api/audit/logs/user/{userId}", 5L)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("types", "DEVICE_UNLOCK", "MONITORING_VIEW")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventType").value("DEVICE_UNLOCK"))
                .andExpect(jsonPath("$[0].deviceIdentifier").value("dev-1"))
                .andExpect(jsonPath("$[1].eventType").value("MONITORING_VIEW"))
                .andExpect(jsonPath("$[1].targetUserId").value(7));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditEventType>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditLoggingService).fetchLogs(eq(5L), eq(from), eq(to), captor.capture());
        assertEquals(List.of(AuditEventType.DEVICE_UNLOCK, AuditEventType.MONITORING_VIEW), captor.getValue());
    }
}