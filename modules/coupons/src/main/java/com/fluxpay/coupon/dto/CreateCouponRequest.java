package com.fluxpay.coupon.dto;

import com.fluxpay.coupon.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class CreateCouponRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Code is required")
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be greater than zero")
    private BigDecimal discountValue;

    private Integer maxUses;
    private Instant validUntil;
}
