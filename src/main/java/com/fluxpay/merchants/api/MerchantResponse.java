package com.fluxpay.merchants.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.service.MerchantView;
import java.time.Instant;

public record MerchantResponse(
        String id,
        String businessName,
        String slug,
        String logoUrl,
        String brandColor,
        int platformFeeBps,
        MerchantStatus status,
        Instant createdAt) {

    public static MerchantResponse from(MerchantView view) {
        return new MerchantResponse(
                PublicId.of(IdPrefix.MERCHANT, view.id()),
                view.businessName(),
                view.slug(),
                view.logoUrl(),
                view.brandColor(),
                view.platformFeeBps(),
                view.status(),
                view.createdAt());
    }
}
