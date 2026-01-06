// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mingyu.app.permissions.dto.PermissionGuideRequest;
import com.mingyu.app.permissions.dto.PermissionGuideResponse;
import com.mingyu.app.permissions.dto.PermissionGuideStepPayload;
import com.mingyu.app.permissions.service.PermissionGuideService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PermissionGuideController.class)
class PermissionGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionGuideService permissionGuideService;

    @Test
    void returnsGuideStateForDevice() throws Exception {
        PermissionGuideResponse response = new PermissionGuideResponse(
                "device-1",
                List.of(
                        new PermissionGuideStepPayload("location", true, Instant.parse("2024-05-01T10:00:00Z")),
                        new PermissionGuideStepPayload("notification", false, null),
                        new PermissionGuideStepPayload("autostart", false, null),
                        new PermissionGuideStepPayload("usage", false, null)
                ),
                Instant.parse("2024-05-01T10:01:00Z")
        );
        when(permissionGuideService.fetchState("device-1")).thenReturn(response);

        mockMvc
                .perform(get("/api/permissions/guide").param("deviceId", "device-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("device-1"))
                .andExpect(jsonPath("$.steps[0].completed").value(true))
                .andExpect(jsonPath("$.steps[1].id").value("notification"));
    }

    @Test
    void updatesStateViaPut() throws Exception {
        PermissionGuideResponse response = new PermissionGuideResponse(
                "device-1",
                List.of(
                        new PermissionGuideStepPayload("location", true, Instant.parse("2024-05-01T10:00:00Z")),
                        new PermissionGuideStepPayload("notification", true, Instant.parse("2024-05-01T10:05:00Z")),
                        new PermissionGuideStepPayload("autostart", false, null),
                        new PermissionGuideStepPayload("usage", false, null)
                ),
                Instant.parse("2024-05-01T10:05:00Z")
        );
        when(permissionGuideService.updateState(any(PermissionGuideRequest.class))).thenReturn(response);

        String body =
                """
                {
                  "deviceId": "device-1",
                  "steps": [
                    {"id": "location", "completed": true, "completedAt": "2024-05-01T10:00:00Z"}
                  ]
                }
                """;

        mockMvc
                .perform(
                        put("/api/permissions/guide")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].id").value("location"))
                .andExpect(jsonPath("$.steps[1].completed").value(true));

        verify(permissionGuideService).updateState(any(PermissionGuideRequest.class));
    }

    @Test
    void requiresDeviceIdOnFetch() throws Exception {
        mockMvc.perform(get("/api/permissions/guide")).andExpect(status().isBadRequest());
    }
}