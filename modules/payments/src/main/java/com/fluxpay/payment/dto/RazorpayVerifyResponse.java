package com.fluxpay.payment.dto;

import com.fluxpay.payment.entity.PaymentIntentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayVerifyResponse {
    private UUID paymentIntentId;
    private UUID orderId;
    private PaymentIntentStatus status;
}
