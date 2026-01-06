package com.mingyu.app.audit.controller;

// Implements 4.会员审计功能

import com.mingyu.app.audit.dto.AuditLogResponse;
import com.mingyu.app.audit.model.AuditEventType;
import com.mingyu.app.audit.model.AuditLogEntity;
import com.mingyu.app.audit.service.AuditLoggingService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/logs")
public class AuditLogController {

    private final AuditLoggingService auditLoggingService;

    public AuditLogController(AuditLoggingService auditLoggingService) {
        this.auditLoggingService = auditLoggingService;
    }

    @GetMapping("/user/{userId}")
    public List<AuditLogResponse> getUserLogs(
            @PathVariable("userId") Long userId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "types", required = false) List<AuditEventType> types) {

        List<AuditLogEntity> entries = auditLoggingService.fetchLogs(userId, from, to, types);
        return entries.stream().map(AuditLogResponse::fromEntity).toList();
    }
}