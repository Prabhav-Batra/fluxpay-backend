package com.fluxpay.webhook.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class WebhookEndpointDto {
    private UUID id;
    private UUID merchantId;
    private String url;
    private String secretKey; // Usually hidden, but included here for initial setup
    private boolean active;
    private Instant createdAt;
}
