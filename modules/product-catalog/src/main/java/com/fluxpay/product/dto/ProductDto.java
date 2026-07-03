package com.fluxpay.product.dto;

import com.fluxpay.product.entity.BillingCycle;
import com.fluxpay.product.entity.ProductType;
import com.fluxpay.product.entity.ProductVisibility;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    private ProductVisibility visibility;
    private String displayImage;
    private String hostedCheckoutUrl;
    private Map<String, Object> metadata;
    private boolean active;
    private Instant createdAt;
}
