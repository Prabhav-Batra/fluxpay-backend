package com.fluxpay.order.service;

import com.fluxpay.order.dto.OrderCreateRequest;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.dto.OrderLineItemDto;
import com.fluxpay.order.dto.OrderLineItemRequest;
import com.fluxpay.order.entity.Order;
import com.fluxpay.order.entity.OrderLineItem;
import com.fluxpay.order.entity.OrderStatus;
import com.fluxpay.order.repository.OrderRepository;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import com.fluxpay.shared.utils.TraceLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Transactional
    public OrderDto createOrder(OrderCreateRequest request) {
        String orderRef = request.getOrderReference() != null && !request.getOrderReference().trim().isEmpty()
                ? request.getOrderReference()
                : generateOrderReference();

        Order order = Order.builder()
                .orderReference(orderRef)
                .merchantId(request.getMerchantId())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.CREATED)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = null;

        for (OrderLineItemRequest itemReq : request.getItems()) {
            ProductDto product = productService.getProduct(itemReq.getProductId());
            
            if (currency == null) {
                currency = product.getCurrency();
            } else if (!currency.equals(product.getCurrency())) {
                throw new IllegalArgumentException("All products in an order must have the same currency");
            }

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderLineItem lineItem = OrderLineItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();

            order.addLineItem(lineItem);
        }

        order.setTotalAmount(totalAmount);
        order.setCurrency(currency);

        order = orderRepository.save(order);
        
        TraceLogger.emit("CREATE_ORDER", 1.0, Map.of(
            "orderId", order.getId(),
            "merchantId", order.getMerchantId(),
            "totalAmount", order.getTotalAmount()
        ));
        
        return mapToDto(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID id) {
        return orderRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersByMerchant(UUID merchantId) {
        return orderRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private String generateOrderReference() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderDto mapToDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .orderReference(order.getOrderReference())
                .merchantId(order.getMerchantId())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .paymentLink(order.getPaymentLink())
                .createdAt(order.getCreatedAt())
                .lineItems(order.getLineItems().stream()
                        .map(this::mapLineItemToDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private OrderLineItemDto mapLineItemToDto(OrderLineItem item) {
        return OrderLineItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
