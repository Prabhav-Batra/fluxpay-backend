package com.fluxpay.sales.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.service.SaleDetailView;

public record SaleDetailResponse(@JsonUnwrapped SaleResponse sale, Payment payment) {

    public record Payment(String gatewayPaymentId, String method, long gatewayFee, PaymentStatus status) {}

    public static SaleDetailResponse from(SaleDetailView view) {
        RecordedPayment payment = view.payment();
        return new SaleDetailResponse(
                SaleResponse.from(view.sale()),
                new Payment(payment.gatewayPaymentId(), payment.method(), payment.gatewayFee(), payment.status()));
    }
}
