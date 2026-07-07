package com.fluxpay.payment.dto;

import com.fluxpay.payment.entity.PaymentIntentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PaymentIntentDto {
    private UUID id;
    private UUID orderId;
    private String gatewayProvider;
    private String gatewayReference;
    private BigDecimal amount;
    private String currency;
    private PaymentIntentStatus status;
    private String paymentLink;
    private String errorMessage;
    private Instant createdAt;
}
