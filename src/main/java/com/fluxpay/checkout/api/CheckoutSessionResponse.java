package com.fluxpay.checkout.api;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;

public record CheckoutSessionResponse(
        String id,
        String url,
        Mode mode,
        CheckoutStatus status,
        String productId,
        String paymentLinkId,
        long amount,
        String currency,
        String customerRef,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt) {

    public static CheckoutSessionResponse from(CheckoutSessionView view) {
        return new CheckoutSessionResponse(
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.id()),
                view.url(),
                view.mode(),
                view.status(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                view.paymentLinkId() == null ? null : PublicId.of(IdPrefix.PAYMENT_LINK, view.paymentLinkId()),
                view.amount(),
                view.currency(),
                view.customerRef(),
                view.successUrl(),
                view.cancelUrl(),
                view.metadata(),
                view.expiresAt(),
                view.completedAt(),
                view.createdAt());
    }
}
