package com.fluxpay.api.checkout.service;

import com.fluxpay.external.adapter.RazorpayAdapter;
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
import com.fluxpay.order.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final RazorpayAdapter razorpayAdapter;

    @Value("${fluxpay.frontend-url}")
    private String frontendUrl;
    
    // In-memory cache for demo purposes. 
    // In production, this should be backed by Redis or Database (e.g. CheckoutSession entity)
    private final ConcurrentHashMap<String, CheckoutSessionDto> sessionStore = new ConcurrentHashMap<>();

    @Transactional
    public CheckoutSessionResponse createSession(CheckoutSessionRequest request) {
        // 1. Fetch Product
        ProductDto product = productService.getProduct(request.getProductId());
        
        UUID merchantId = product.getMerchantId();

        // 2. Create Order
        OrderLineItemRequest lineItem = new OrderLineItemRequest();
        lineItem.setProductId(product.getId());
        lineItem.setQuantity(1);
                
        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setMerchantId(merchantId);
        orderRequest.setCustomerEmail(request.getCustomerEmail());
        orderRequest.setItems(List.of(lineItem));
        // Append a unique suffix so multiple checkouts by the same user don't violate the DB unique constraint
        String baseRef = request.getMerchantReference() != null && !request.getMerchantReference().trim().isEmpty() 
            ? request.getMerchantReference().trim() 
            : "ORD";
        orderRequest.setOrderReference(baseRef + "_" + UUID.randomUUID().toString().substring(0, 8));              
        OrderDto order = orderService.createOrder(orderRequest);

        // Generate Session ID early
        String sessionId = "cs_" + UUID.randomUUID().toString().replace("-", "");
        String successUrl = request.getSuccessUrl().replace("{CHECKOUT_SESSION_ID}", sessionId);

        // 3. Create PaymentIntent via Gateway Router
        ProcessPaymentRequest piRequest = new ProcessPaymentRequest();
        piRequest.setOrderId(order.getId());
        // preferredGateway left empty -> fluxpay.gateways.default (RAZORPAY)
        piRequest.setReturnUrl(successUrl);
                
        PaymentIntentDto paymentIntent = paymentService.processPayment(piRequest);

        // 4. Create Session
        boolean razorpay = RazorpayAdapter.PROVIDER.equals(paymentIntent.getGatewayProvider());

        CheckoutSessionDto sessionDto = CheckoutSessionDto.builder()
                .sessionId(sessionId)
                .merchantId(merchantId)
                .customerEmail(request.getCustomerEmail())
                .product(product)
                .amountTotal(order.getTotalAmount())
                .currency(order.getCurrency())
                .status("open")
                .orderId(order.getId())
                .gateway(paymentIntent.getGatewayProvider())
                .paymentSessionId(razorpay ? null : paymentIntent.getPaymentLink())
                .razorpayOrderId(razorpay ? paymentIntent.getGatewayReference() : null)
                .razorpayKeyId(razorpay ? razorpayAdapter.getKeyId() : null)
                .amountSubunits(RazorpayAdapter.toSubunits(order.getTotalAmount(), order.getCurrency()))
                .successUrl(successUrl)
                .cancelUrl(request.getCancelUrl())
                .build();
                
        sessionStore.put(sessionId, sessionDto);
        
        String checkoutUrl = frontendUrl + "/checkout/" + sessionId;
        
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
        // Reflect payment so a revisited checkout page doesn't offer to pay twice
        if (!"complete".equals(session.getStatus())
                && orderService.getOrder(session.getOrderId()).getStatus() == OrderStatus.PAID) {
            session.setStatus("complete");
        }
        return session;
    }
}
