package com.fluxpay.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class OrderCreateRequest {
    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @Email(message = "Invalid email format")
    private String customerEmail;

    @NotEmpty(message = "At least one item is required")
    private List<OrderLineItemRequest> items;

    private String orderReference;
}
