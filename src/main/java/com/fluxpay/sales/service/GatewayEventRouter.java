package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import org.springframework.stereotype.Component;

@Component
public class GatewayEventRouter implements GatewayEventHandler {

    private final SaleCaptureService captureService;
    private final SaleRefundService refundService;

    public GatewayEventRouter(SaleCaptureService captureService, SaleRefundService refundService) {
        this.captureService = captureService;
        this.refundService = refundService;
    }

    @Override
    public void onPaymentCaptured(Mode mode, GatewayPayment payment) {
        captureService.capture(mode, payment);
    }

    @Override
    public void onRefundProcessed(Mode mode, GatewayRefund refund) {
        refundService.refund(mode, refund);
    }

    @Override
    public void onPaymentFailed(Mode mode, GatewayPayment payment) {
        captureService.failPayment(mode, payment);
    }
}
