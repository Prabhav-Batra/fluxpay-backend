package com.fluxpay.invoice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class GenerateInvoiceRequest {
    @NotNull(message = "Order ID is required")
    private UUID orderId;
}
