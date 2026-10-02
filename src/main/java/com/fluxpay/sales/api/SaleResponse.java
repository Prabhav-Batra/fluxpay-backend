package com.fluxpay.sales.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.SaleStatus;
import com.fluxpay.sales.service.SaleView;
import java.time.Instant;
import java.util.Map;

public record SaleResponse(
        String id,
        Mode mode,
        String productId,
        String checkoutSessionId,
        String customerRef,
        long amount,
        long refundedAmount,
        String currency,
        SaleStatus status,
        Map<String, String> metadata,
        Instant createdAt) {

    public static SaleResponse from(SaleView view) {
        return new SaleResponse(
                PublicId.of(IdPrefix.SALE, view.id()),
                view.mode(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.checkoutSessionId()),
                view.customerRef(),
                view.amount(),
                view.refundedAmount(),
                view.currency(),
                view.status(),
                view.metadata(),
                view.createdAt());
    }
}
