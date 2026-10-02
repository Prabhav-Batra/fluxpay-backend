package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;

/** Implemented outside the payments module (by sales) so payments never depends on sales. */
public interface GatewayEventHandler {

    void onPaymentCaptured(Mode mode, GatewayPayment payment);

    void onRefundProcessed(Mode mode, GatewayRefund refund);
}
