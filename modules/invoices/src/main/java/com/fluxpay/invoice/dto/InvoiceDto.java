package com.fluxpay.invoice.dto;

import com.fluxpay.invoice.entity.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvoiceDto {
    private UUID id;
    private UUID merchantId;
    private String customerEmail;
    private UUID orderId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private String currency;
    private InvoiceStatus status;
    private Instant createdAt;
}
