package com.fluxpay.audit.controller;

import com.fluxpay.audit.dto.AuditLogDto;
import com.fluxpay.audit.dto.CreateAuditLogRequest;
import com.fluxpay.audit.service.AuditLogService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    public ResponseEntity<ApiResponse<AuditLogDto>> createAuditLog(@Valid @RequestBody CreateAuditLogRequest request) {
        AuditLogDto log = auditLogService.createAuditLog(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(log, "Audit log created successfully"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getUserAuditLogs(@PathVariable UUID userId) {
        List<AuditLogDto> logs = auditLogService.getUserAuditLogs(userId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getMerchantAuditLogs(@PathVariable UUID merchantId) {
        // For now, merchantId is synonymous with userId in this context
        List<AuditLogDto> logs = auditLogService.getUserAuditLogs(merchantId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
