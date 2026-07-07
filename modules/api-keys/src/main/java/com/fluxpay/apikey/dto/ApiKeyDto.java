package com.fluxpay.apikey.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ApiKeyDto {
    private UUID id;
    private String keyPrefix;
    private String mode;
    private UUID merchantId;
    private boolean active;
    private Instant createdAt;
}
