package com.fluxpay.audit.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AuditLogDto {
    private UUID id;
    private UUID userId;
    private String action;
    private String resource;
    private String resourceId;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
