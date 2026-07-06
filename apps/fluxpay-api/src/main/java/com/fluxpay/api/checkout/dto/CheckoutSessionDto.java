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
    private String paymentSessionId; // Cashfree requires this for JS SDK
}
