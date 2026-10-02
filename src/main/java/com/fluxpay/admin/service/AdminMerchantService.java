package com.fluxpay.admin.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.service.NewPayout;
import com.fluxpay.ledger.service.PayoutView;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.service.MerchantView;
import java.util.UUID;

/** Platform-admin operations across merchants (spec §10). Callers are authorised as PLATFORM_ADMIN. */
public interface AdminMerchantService {

    CursorPage<MerchantView> list(PageQuery query);

    AdminMerchantDetail get(UUID merchantId);

    MerchantView update(UUID merchantId, Integer platformFeeBps, MerchantStatus status);

    PayoutView recordPayout(UUID merchantId, Mode mode, NewPayout payout);

    CursorPage<PayoutView> payouts(UUID merchantId, Mode mode, PageQuery query);
}
