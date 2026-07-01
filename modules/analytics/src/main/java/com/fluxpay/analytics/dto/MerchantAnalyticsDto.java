package com.fluxpay.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MerchantAnalyticsDto {
    private UUID merchantId;
    private long totalTransactions;
    private BigDecimal totalVolume;
    private long activeSubscriptions;
    private long failedTransactions;
}
