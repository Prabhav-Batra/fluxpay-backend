package com.fluxpay.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApplicationCreateRequest {
    
    @NotBlank(message = "Application name is required")
    private String name;

    @NotNull(message = "Organization ID is required")
    private UUID organizationId;
}
