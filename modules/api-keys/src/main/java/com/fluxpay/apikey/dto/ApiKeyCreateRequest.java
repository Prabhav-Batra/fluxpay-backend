package com.fluxpay.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApiKeyCreateRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Mode (LIVE or TEST) is required")
    private String mode;
}
