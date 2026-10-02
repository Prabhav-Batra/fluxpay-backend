package com.fluxpay.ledger.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface LedgerService {

    /** Writes gross, platform fee and gateway fee entries in the caller's transaction. */
    SaleEntries recordSale(TenantContext tenant, UUID saleId, long gross, String currency, long gatewayFee);

    /** Writes a refund entry in the caller's transaction. False when this gateway refund was already recorded. */
    boolean recordRefund(TenantContext tenant, UUID saleId, String gatewayRefundId, long amount, String currency);

    Balance balance(TenantContext tenant);

    CursorPage<LedgerEntryView> entries(TenantContext tenant, PageQuery query);
}
