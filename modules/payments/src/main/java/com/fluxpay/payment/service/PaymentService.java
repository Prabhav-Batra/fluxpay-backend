package com.fluxpay.payment.service;

import com.fluxpay.external.gateway.PaymentGatewayPort;
import com.fluxpay.gatewayframework.service.GatewayRouter;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.PaymentIntentDto;
import com.fluxpay.payment.dto.ProcessPaymentRequest;
import com.fluxpay.payment.entity.PaymentIntent;
import com.fluxpay.payment.entity.PaymentIntentStatus;
import com.fluxpay.payment.repository.PaymentIntentRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import com.fluxpay.shared.utils.TraceLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final GatewayRouter gatewayRouter;
    private final OrderService orderService;

    @Transactional
    public PaymentIntentDto processPayment(ProcessPaymentRequest request) {
        OrderDto order = orderService.getOrder(request.getOrderId());
        
        if (!"CREATED".equals(order.getStatus().name()) && !"FAILED".equals(order.getStatus().name())) {
            throw new BusinessException("Order cannot be processed in current state", "INVALID_ORDER_STATE");
        }

        // 1. Route to Gateway
        PaymentGatewayPort gateway = gatewayRouter.route(request.getPreferredGateway());

        // 2. Generate Payment Link
        String paymentLink;
        try {
            paymentLink = gateway.generatePaymentLink(
                    order.getId(),
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getCustomerEmail(),
                    request.getReturnUrl()
            );
        } catch (Exception ex) {
            throw new BusinessException("Gateway failed to generate payment link", "GATEWAY_ERROR");
        }

        // 3. Create Intent
        PaymentIntent intent = PaymentIntent.builder()
                .orderId(order.getId())
                .gatewayProvider(gateway.getProviderName())
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(PaymentIntentStatus.INITIATED)
                .build();

        intent = paymentIntentRepository.save(intent);

        TraceLogger.emit("PROCESS_PAYMENT", 1.0, Map.of(
            "intentId", intent.getId(),
            "orderId", intent.getOrderId(),
            "gatewayProvider", intent.getGatewayProvider()
        ));

        // Map and include the volatile paymentLink
        PaymentIntentDto dto = mapToDto(intent);
        dto.setPaymentLink(paymentLink);
        return dto;
    }

    @Transactional(readOnly = true)
    public PaymentIntentDto getPaymentIntent(UUID id) {
        return paymentIntentRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<PaymentIntentDto> getAllPayments(UUID merchantId) {
        List<UUID> orderIds = orderService.getOrdersByMerchant(merchantId).stream()
                .map(OrderDto::getId)
                .collect(Collectors.toList());
        
        if (orderIds.isEmpty()) {
            return List.of();
        }
        
        return paymentIntentRepository.findByOrderIdIn(orderIds).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PaymentIntentDto mapToDto(PaymentIntent intent) {
        return PaymentIntentDto.builder()
                .id(intent.getId())
                .orderId(intent.getOrderId())
                .gatewayProvider(intent.getGatewayProvider())
                .gatewayReference(intent.getGatewayReference())
                .amount(intent.getAmount())
                .currency(intent.getCurrency())
                .status(intent.getStatus())
                .errorMessage(intent.getErrorMessage())
                .createdAt(intent.getCreatedAt())
                .build();
    }
}
