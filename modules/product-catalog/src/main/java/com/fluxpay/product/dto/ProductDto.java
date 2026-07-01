package com.fluxpay.product.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ProductDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private UUID merchantId;
    private Map<String, Object> metadata;
    private boolean active;
    private Instant createdAt;
}
