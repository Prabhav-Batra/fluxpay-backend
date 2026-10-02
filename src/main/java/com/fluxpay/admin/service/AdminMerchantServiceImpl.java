package com.fluxpay.admin.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.ledger.service.NewPayout;
import com.fluxpay.ledger.service.PayoutView;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.MerchantView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMerchantServiceImpl implements AdminMerchantService {

    private final MerchantService merchantService;
    private final LedgerService ledgerService;

    public AdminMerchantServiceImpl(MerchantService merchantService, LedgerService ledgerService) {
        this.merchantService = merchantService;
        this.ledgerService = ledgerService;
    }

    @Override
    public CursorPage<MerchantView> list(PageQuery query) {
        return merchantService.list(query);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMerchantDetail get(UUID merchantId) {
        MerchantView merchant = merchantService.get(merchantId);
        return new AdminMerchantDetail(
                merchant,
                ledgerService.balance(new TenantContext(merchantId, Mode.TEST)),
                ledgerService.balance(new TenantContext(merchantId, Mode.LIVE)));
    }

    @Override
    public MerchantView update(UUID merchantId, Integer platformFeeBps, MerchantStatus status) {
        return merchantService.updateByAdmin(merchantId, platformFeeBps, status);
    }

    @Override
    public PayoutView recordPayout(UUID merchantId, Mode mode, NewPayout payout) {
        return ledgerService.recordPayout(new TenantContext(merchantId, mode), payout);
    }

    @Override
    public CursorPage<PayoutView> payouts(UUID merchantId, Mode mode, PageQuery query) {
        merchantService.get(merchantId);
        return ledgerService.payouts(new TenantContext(merchantId, mode), query);
    }
}
