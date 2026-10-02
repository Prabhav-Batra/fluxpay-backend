package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayRefund;

public interface SaleRefundService {

    /** Applies a gateway refund to its sale. Safe to call any number of times for the same refund. */
    void refund(Mode mode, GatewayRefund refund);
}
