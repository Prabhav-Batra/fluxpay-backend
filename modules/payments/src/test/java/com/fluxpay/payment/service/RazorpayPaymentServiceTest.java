package com.fluxpay.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.external.adapter.RazorpayAdapter;
import com.fluxpay.external.adapter.RazorpaySignatureVerifier;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.RazorpayVerifyRequest;
import com.fluxpay.payment.entity.PaymentIntent;
import com.fluxpay.payment.entity.PaymentIntentStatus;
import com.fluxpay.payment.repository.PaymentIntentRepository;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.webhook.service.WebhookEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayPaymentServiceTest {

    private final PaymentIntentRepository repository = mock(PaymentIntentRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final WebhookEventPublisher publisher = mock(WebhookEventPublisher.class);
    private final RazorpaySignatureVerifier verifier = mock(RazorpaySignatureVerifier.class);
    private final RazorpayPaymentService service =
            new RazorpayPaymentService(repository, orderService, publisher, verifier, new ObjectMapper());

    private final UUID orderId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private PaymentIntent intent;

    @BeforeEach
    void setUp() {
        intent = PaymentIntent.builder()
                .id(UUID.randomUUID()).orderId(orderId).gatewayProvider("RAZORPAY")
                .gatewayReference("order_ABC").amount(new BigDecimal("499.00")).currency("INR")
                .status(PaymentIntentStatus.INITIATED).build();
        when(repository.findFirstByGatewayProviderAndGatewayReference(RazorpayAdapter.PROVIDER, "order_ABC"))
                .thenReturn(Optional.of(intent));
        when(orderService.markPaid(orderId)).thenReturn(OrderDto.builder()
                .id(orderId).merchantId(merchantId).orderReference("user42_ab12cd34")
                .customerEmail("buyer@example.com").build());
    }

    private static RazorpayVerifyRequest request(String orderId, String paymentId, String signature) {
        RazorpayVerifyRequest r = new RazorpayVerifyRequest();
        r.setRazorpayOrderId(orderId);
        r.setRazorpayPaymentId(paymentId);
        r.setRazorpaySignature(signature);
        return r;
    }

    @Test
    void validSignatureCapturesPaymentAndNotifiesMerchant() {
        when(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", "sig")).thenReturn(true);

        var response = service.verifyCheckoutPayment(request("order_ABC", "pay_XYZ", "sig"));

        assertEquals(PaymentIntentStatus.CAPTURED, response.getStatus());
        assertEquals("pay_XYZ", intent.getGatewayPaymentId());
        verify(orderService).markPaid(orderId);
        verify(publisher).publishEvent(eq(merchantId), eq("payment.succeeded"),
                (Object) org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(p ->
                        "user42_ab12cd34".equals(p.get("order_reference")) && "SUCCESS".equals(p.get("status"))));
    }

    @Test
    void signatureMismatchIsRejectedAndNothingIsMarkedPaid() {
        when(verifier.isValidPaymentSignature(anyString(), anyString(), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCheckoutPayment(request("order_ABC", "pay_XYZ", "forged")));

        assertEquals("INVALID_SIGNATURE", ex.getErrorCode());
        assertEquals(PaymentIntentStatus.INITIATED, intent.getStatus());
        verify(orderService, never()).markPaid(any());
    }

    @Test
    void missingFieldsAreRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyCheckoutPayment(request("order_ABC", null, "sig")));
        assertEquals("MISSING_FIELDS", ex.getErrorCode());
    }

    @Test
    void verifyAndWebhookTogetherNotifyMerchantOnce() throws Exception {
        when(verifier.isValidPaymentSignature("order_ABC", "pay_XYZ", "sig")).thenReturn(true);
        when(verifier.isValidWebhookSignature(anyString(), eq("wsig"))).thenReturn(true);

        service.verifyCheckoutPayment(request("order_ABC", "pay_XYZ", "sig"));
        boolean accepted = service.handleWebhook("""
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_XYZ","order_id":"order_ABC"}}}}
                """, "wsig");

        assertTrue(accepted);
        verify(orderService, times(1)).markPaid(orderId);
        verify(publisher, times(1)).publishEvent(any(), anyString(), any());
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        when(verifier.isValidWebhookSignature(anyString(), anyString())).thenReturn(false);
        assertEquals(false, service.handleWebhook("{}", "bad"));
        verify(repository, never()).save(any());
    }
}
