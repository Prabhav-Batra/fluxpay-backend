package com.fluxpay.order.service;

import com.fluxpay.order.entity.Order;
import com.fluxpay.order.entity.OrderStatus;
import com.fluxpay.order.repository.OrderRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.webhook.service.WebhookEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final WebhookEventPublisher webhookEventPublisher;

    @Transactional
    public void fulfillOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order {} is already completed. Skipping fulfillment.", orderId);
            return;
        }

        // 1. Mark Order as PAID
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        
        log.info("Order {} marked as COMPLETED", orderId);

        // 2. Dispatch Webhook to Merchant
        // We dispatch a "product.purchased" event so the merchant's game server can grant the assets.
        Map<String, Object> payload = Map.of(
            "order_id", order.getId().toString(),
            "customer_email", order.getCustomerEmail(),
            "amount", order.getTotalAmount(),
            "currency", order.getCurrency()
        );
        
        try {
            webhookEventPublisher.publishEvent(order.getMerchantId(), "product.purchased", payload);
            log.info("Successfully published product.purchased webhook for order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish webhook for order {}", orderId, e);
        }
    }
}
