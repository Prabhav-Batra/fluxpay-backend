package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.PaymentLinkView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record PaymentLinkResponse(
        String id,
        Mode mode,
        String productId,
        String slug,
        String url,
        String successUrl,
        String cancelUrl,
        boolean active,
        Instant createdAt) {

    public static PaymentLinkResponse from(PaymentLinkView view) {
        return new PaymentLinkResponse(
                PublicId.of(IdPrefix.PAYMENT_LINK, view.id()),
                view.mode(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                view.slug(),
                view.url(),
                view.successUrl(),
                view.cancelUrl(),
                view.active(),
                view.createdAt());
    }
}
