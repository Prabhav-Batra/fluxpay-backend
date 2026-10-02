package com.fluxpay.sales.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface SalesQueryService {

    CursorPage<SaleView> list(TenantContext tenant, PageQuery query, SaleFilter filter);

    /** SALE_NOT_FOUND (not found) unless the sale belongs to this tenant and mode. */
    SaleDetailView get(TenantContext tenant, UUID saleId);
}
