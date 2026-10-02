package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.sales.domain.Sale;
import java.util.LinkedHashMap;
import java.util.Map;

/** The `data` object of merchant webhook events (spec §7). */
public final class SalePayloads {

    private SalePayloads() {}

    public static Map<String, Object> completed(Sale sale) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sale_id", PublicId.of(IdPrefix.SALE, sale.getId()));
        data.put("checkout_session_id", PublicId.of(IdPrefix.CHECKOUT_SESSION, sale.getCheckoutSessionId()));
        data.put("product_id", PublicId.of(IdPrefix.PRODUCT, sale.getProductId()));
        data.put("customer_ref", sale.getCustomerRef());
        data.put("amount", sale.getAmount());
        data.put("currency", sale.getCurrency());
        data.put("metadata", sale.getMetadata());
        return data;
    }

    public static Map<String, Object> refunded(Sale sale, long refundAmount) {
        Map<String, Object> data = completed(sale);
        data.put("refund_amount", refundAmount);
        data.put("amount_refunded", sale.getRefundedAmount());
        data.put("status", sale.getStatus().value());
        return data;
    }

    public static Map<String, Object> expired(CheckoutSessionView session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkout_session_id", PublicId.of(IdPrefix.CHECKOUT_SESSION, session.id()));
        data.put("product_id", PublicId.of(IdPrefix.PRODUCT, session.productId()));
        data.put("customer_ref", session.customerRef());
        data.put("amount", session.amount());
        data.put("currency", session.currency());
        data.put("metadata", session.metadata());
        return data;
    }
}
