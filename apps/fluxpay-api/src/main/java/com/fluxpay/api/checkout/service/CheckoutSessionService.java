package com.fluxpay.api.checkout.service;

import com.fluxpay.order.dto.OrderCreateRequest;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.dto.OrderLineItemRequest;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.PaymentIntentDto;
import com.fluxpay.payment.dto.ProcessPaymentRequest;
import com.fluxpay.payment.service.PaymentService;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.api.checkout.dto.CheckoutSessionDto;
import com.fluxpay.api.checkout.dto.CheckoutSessionRequest;
import com.fluxpay.api.checkout.dto.CheckoutSessionResponse;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CheckoutSessionService {

    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    
    // In-memory cache for demo purposes. 
    // In production, this should be backed by Redis or Database (e.g. CheckoutSession entity)
    private final ConcurrentHashMap<String, CheckoutSessionDto> sessionStore = new ConcurrentHashMap<>();

    @Transactional
    public CheckoutSessionResponse createSession(CheckoutSessionRequest request) {
        // TODO: Replace with actual Merchant ID from API Key Authentication Token
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // 1. Fetch Product
        ProductDto product = productService.getProduct(request.getProductId());

        // 2. Create Order
        OrderLineItemRequest lineItem = new OrderLineItemRequest();
        lineItem.setProductId(product.getId());
        lineItem.setQuantity(1);
                
        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setMerchantId(merchantId);
        orderRequest.setCustomerEmail(request.getCustomerEmail());
        orderRequest.setItems(List.of(lineItem));
                
        OrderDto order = orderService.createOrder(orderRequest);

        // 3. Create PaymentIntent via Gateway Router
        ProcessPaymentRequest piRequest = new ProcessPaymentRequest();
        piRequest.setOrderId(order.getId());
        piRequest.setPreferredGateway("CASHFREE");
                
        PaymentIntentDto paymentIntent = paymentService.processPayment(piRequest);

        // 4. Generate Session
        String sessionId = "cs_" + UUID.randomUUID().toString().replace("-", "");
        
        CheckoutSessionDto sessionDto = CheckoutSessionDto.builder()
                .sessionId(sessionId)
                .merchantId(merchantId)
                .customerEmail(request.getCustomerEmail())
                .product(product)
                .amountTotal(order.getTotalAmount())
                .currency(order.getCurrency())
                .status("open")
                .orderId(order.getId())
                .paymentSessionId(paymentIntent.getPaymentLink()) // The CashfreeAdapter stores the session ID in the link field
                .build();
                
        sessionStore.put(sessionId, sessionDto);
        
        String checkoutUrl = "http://localhost:3000/checkout/" + sessionId;
        
        return CheckoutSessionResponse.builder()
                .sessionId(sessionId)
                .checkoutUrl(checkoutUrl)
                .build();
    }

    public CheckoutSessionDto getSession(String sessionId) {
        CheckoutSessionDto session = sessionStore.get(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("CheckoutSession", sessionId);
        }
        return session;
    }
}
