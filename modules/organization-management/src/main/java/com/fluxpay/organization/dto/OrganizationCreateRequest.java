package com.fluxpay.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OrganizationCreateRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;
}
