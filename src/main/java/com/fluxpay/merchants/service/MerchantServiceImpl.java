package com.fluxpay.merchants.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.persistence.MerchantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchants;
    private final Clock clock;

    public MerchantServiceImpl(MerchantRepository merchants, Clock clock) {
        this.merchants = merchants;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantView get(UUID merchantId) {
        return MerchantView.from(find(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateProfile(UUID merchantId, ProfileUpdate update) {
        Merchant merchant = find(merchantId);
        Instant now = Instant.now(clock);
        if (update.businessName() != null) {
            merchant.rename(update.businessName().trim(), now);
        }
        if (update.logoUrl() != null || update.brandColor() != null) {
            merchant.changeBranding(
                    resolve(update.logoUrl(), merchant.getLogoUrl()),
                    resolve(update.brandColor(), merchant.getBrandColor()),
                    now);
        }
        return MerchantView.from(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID merchantId) {
        return merchants
                .findById(merchantId)
                .map(merchant -> merchant.getStatus() == MerchantStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<MerchantView> list(PageQuery query) {
        return CursorPage.from(
                merchants.page(query.before(), Limit.of(query.fetchSize())),
                query,
                MerchantView::from,
                view -> PublicId.of(IdPrefix.MERCHANT, view.id()));
    }

    @Override
    @Transactional
    public MerchantView updateByAdmin(UUID merchantId, Integer platformFeeBps, MerchantStatus status) {
        Merchant merchant = find(merchantId);
        Instant now = Instant.now(clock);
        if (platformFeeBps != null) {
            merchant.changePlatformFee(platformFeeBps, now);
        }
        if (status != null) {
            merchant.changeStatus(status, now);
        }
        return MerchantView.from(merchant);
    }

    private Merchant find(UUID merchantId) {
        return merchants
                .findById(merchantId)
                .orElseThrow(() -> FluxpayException.notFound("MERCHANT_NOT_FOUND", "Merchant not found"));
    }

    private static String resolve(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isEmpty() ? null : requested;
    }
}
