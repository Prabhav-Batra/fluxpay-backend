package com.fluxpay.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateAuditLogRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Action is required")
    private String action;

    @NotBlank(message = "Resource is required")
    private String resource;

    private String resourceId;
    private Map<String, Object> metadata;
}
