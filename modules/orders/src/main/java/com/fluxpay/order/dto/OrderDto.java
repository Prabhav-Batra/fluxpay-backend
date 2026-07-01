package com.fluxpay.order.dto;

import com.fluxpay.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderDto {
    private UUID id;
    private String orderReference;
    private UUID merchantId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String currency;
    private OrderStatus status;
    private String paymentLink;
    private List<OrderLineItemDto> lineItems;
    private Instant createdAt;
}
