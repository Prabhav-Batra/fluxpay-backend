package com.fluxpay.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.fluxpay.product.entity.BillingCycle;
import com.fluxpay.product.entity.ProductType;
import com.fluxpay.product.entity.ProductVisibility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class ProductCreateRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be positive")
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    private String currency;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotNull(message = "Product Type is required")
    private ProductType productType;

    private BillingCycle billingCycle;
    private Integer trialPeriodDays;
    private Integer gracePeriodDays;
    private Map<String, Object> renewalRules;
    private Map<String, Object> cancellationRules;
    private List<String> benefits;
    
    private Integer baseCredits;
    private Integer bonusCredits;
    private Integer purchaseLimit;

    @NotNull(message = "Visibility is required")
    private ProductVisibility visibility;

    private String displayImage;

    // List of assets to grant and their quantities
    private List<ProductAssetLinkRequest> assets;

    private Map<String, Object> metadata;
}
