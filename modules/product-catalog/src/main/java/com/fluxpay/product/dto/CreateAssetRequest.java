package com.fluxpay.product.dto;

import com.fluxpay.product.entity.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateAssetRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Asset name is required")
    private String name;

    @NotBlank(message = "Internal key is required")
    private String internalKey;

    private String description;

    @NotNull(message = "Asset type is required")
    private AssetType assetType;

    private String defaultValue;
    private String maxValue;
    private String deliveryMethod;
    private String displayIcon;
    private String displayColor;
    private Map<String, Object> metadata;
}
