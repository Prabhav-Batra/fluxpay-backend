package com.fluxpay.catalog.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;

public interface PaymentLinkService {

    /** PRODUCT_NOT_FOUND (not found) unless the product belongs to this tenant and mode. */
    PaymentLinkView create(TenantContext tenant, NewPaymentLink link);

    PaymentLinkView get(TenantContext tenant, UUID linkId);

    CursorPage<PaymentLinkView> list(TenantContext tenant, PageQuery query);

    PaymentLinkView update(TenantContext tenant, UUID linkId, PaymentLinkChanges changes);

    Optional<PaymentLinkView> findActiveBySlug(String slug);
}
