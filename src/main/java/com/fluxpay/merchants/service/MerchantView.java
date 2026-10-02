package com.fluxpay.merchants.service;

import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.domain.MerchantStatus;
import java.time.Instant;
import java.util.UUID;

public record MerchantView(
        UUID id,
        String businessName,
        String slug,
        String logoUrl,
        String brandColor,
        int platformFeeBps,
        MerchantStatus status,
        Instant createdAt) {

    static MerchantView from(Merchant merchant) {
        return new MerchantView(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getSlug(),
                merchant.getLogoUrl(),
                merchant.getBrandColor(),
                merchant.getPlatformFeeBps(),
                merchant.getStatus(),
                merchant.getCreatedAt());
    }
}
