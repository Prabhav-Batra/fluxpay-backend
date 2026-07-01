package com.fluxpay.merchant.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class MerchantDto {
    private UUID id;
    private String email;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
