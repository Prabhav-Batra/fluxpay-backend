package com.fluxpay.api.checkout.dto;

import com.fluxpay.product.dto.ProductDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionDto {
    private String sessionId;
    private UUID merchantId;
    private String customerEmail;
    private ProductDto product;
    private BigDecimal amountTotal;
    private String currency;
    private String status;
    private UUID orderId;
    private String gateway;          // e.g. "RAZORPAY" - tells the checkout page which SDK to open
    private String paymentSessionId; // Cashfree only: payment_session_id for its JS SDK
    private String razorpayOrderId;  // Razorpay only: order_id passed to checkout.js
    private String razorpayKeyId;    // Razorpay only: public key id (never the secret)
    private Long amountSubunits;     // amount in the smallest currency unit (paise for INR)
    private String successUrl;
    private String cancelUrl;
}
