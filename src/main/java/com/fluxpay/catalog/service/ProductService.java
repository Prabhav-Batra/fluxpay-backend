package com.fluxpay.catalog.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface ProductService {

    ProductView create(TenantContext tenant, NewProduct product);

    /** PRODUCT_NOT_FOUND (not found) unless the product belongs to this tenant and mode. */
    ProductView get(TenantContext tenant, UUID productId);

    /** {@code active} null lists all products. */
    CursorPage<ProductView> list(TenantContext tenant, PageQuery query, Boolean active);

    ProductView update(TenantContext tenant, UUID productId, ProductChanges changes);
}
