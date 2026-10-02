package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayEventRouter implements GatewayEventHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventRouter.class);

    private final SaleCaptureService captureService;

    public GatewayEventRouter(SaleCaptureService captureService) {
        this.captureService = captureService;
    }

    @Override
    public void onPaymentCaptured(Mode mode, GatewayPayment payment) {
        captureService.capture(mode, payment);
    }

    @Override
    public void onRefundProcessed(Mode mode, GatewayRefund refund) {
        log.info("Refund {} received; refund handling arrives in the next task", refund.id());
    }
}
