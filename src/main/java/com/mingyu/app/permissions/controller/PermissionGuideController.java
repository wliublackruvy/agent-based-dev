// Implements 2.权限引导与存活看板
package com.mingyu.app.permissions.controller;

import com.mingyu.app.permissions.dto.PermissionGuideRequest;
import com.mingyu.app.permissions.dto.PermissionGuideResponse;
import com.mingyu.app.permissions.service.PermissionGuideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions/guide")
@Validated
public class PermissionGuideController {

    private final PermissionGuideService permissionGuideService;

    public PermissionGuideController(PermissionGuideService permissionGuideService) {
        this.permissionGuideService = permissionGuideService;
    }

    @GetMapping
    public PermissionGuideResponse fetchState(@RequestParam("deviceId") @NotBlank String deviceId) {
        return permissionGuideService.fetchState(deviceId);
    }

    @PutMapping
    public PermissionGuideResponse persistState(
            @Valid @RequestBody PermissionGuideRequest request) {
        return permissionGuideService.updateState(request);
    }
}