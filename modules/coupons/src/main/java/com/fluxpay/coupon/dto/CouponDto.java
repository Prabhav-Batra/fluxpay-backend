package com.fluxpay.coupon.dto;

import com.fluxpay.coupon.entity.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class CouponDto {
    private UUID id;
    private UUID merchantId;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer maxUses;
    private Integer timesUsed;
    private Instant validUntil;
    private boolean active;
    private Instant createdAt;
}
