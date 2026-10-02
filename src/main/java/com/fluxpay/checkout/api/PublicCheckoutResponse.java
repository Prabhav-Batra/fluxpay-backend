package com.fluxpay.checkout.api;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.PublicCheckoutView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record PublicCheckoutResponse(
        String id,
        Mode mode,
        CheckoutStatus status,
        long amount,
        String currency,
        Product product,
        Merchant merchant,
        String successUrl,
        String cancelUrl,
        Instant expiresAt) {

    public record Product(String name, String description, String imageUrl) {}

    public record Merchant(String name, String logoUrl, String brandColor) {}

    public static PublicCheckoutResponse from(PublicCheckoutView view) {
        return new PublicCheckoutResponse(
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.id()),
                view.mode(),
                view.status(),
                view.amount(),
                view.currency(),
                new Product(view.productName(), view.productDescription(), view.productImageUrl()),
                new Merchant(view.merchantName(), view.merchantLogoUrl(), view.merchantBrandColor()),
                view.successUrl(),
                view.cancelUrl(),
                view.expiresAt());
    }
}
