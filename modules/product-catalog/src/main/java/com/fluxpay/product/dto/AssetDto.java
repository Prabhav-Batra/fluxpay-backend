package com.fluxpay.product.dto;

import com.fluxpay.product.entity.AssetType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AssetDto {
    private UUID id;
    private UUID merchantId;
    private String name;
    private String internalKey;
    private String description;
    private AssetType assetType;
    private String defaultValue;
    private String maxValue;
    private String deliveryMethod;
    private String displayIcon;
    private String displayColor;
    private Map<String, Object> metadata;
    private boolean active;
    private Instant createdAt;
}
