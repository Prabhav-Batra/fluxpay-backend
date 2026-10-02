package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.domain.SaleStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SaleView(
        UUID id,
        Mode mode,
        UUID productId,
        UUID checkoutSessionId,
        String customerRef,
        long amount,
        long refundedAmount,
        String currency,
        SaleStatus status,
        Map<String, String> metadata,
        Instant createdAt) {

    static SaleView from(Sale sale) {
        return new SaleView(
                sale.getId(),
                sale.getMode(),
                sale.getProductId(),
                sale.getCheckoutSessionId(),
                sale.getCustomerRef(),
                sale.getAmount(),
                sale.getRefundedAmount(),
                sale.getCurrency(),
                sale.getStatus(),
                Map.copyOf(sale.getMetadata()),
                sale.getCreatedAt());
    }
}
