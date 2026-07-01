package com.fluxpay.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApplyCouponRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Code is required")
    private String code;
}
