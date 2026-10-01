package com.fluxpay.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** The three fields Razorpay checkout.js passes to its success handler, forwarded unchanged. */
@Data
public class RazorpayVerifyRequest {
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @JsonProperty("razorpay_signature")
    private String razorpaySignature;
}
