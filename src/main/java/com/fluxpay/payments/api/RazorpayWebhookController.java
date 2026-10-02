package com.fluxpay.payments.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayWebhookEvent;
import com.fluxpay.payments.service.PaymentGateway;
import com.fluxpay.payments.service.RazorpayWebhookParser;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay calls this for every event. The signature over the raw body is the only authentication, so it is
 * checked before parsing. Processing errors return 5xx so Razorpay retries; processing is idempotent.
 */
@RestController
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final PaymentGateway gateway;
    private final RazorpayWebhookParser parser;
    private final GatewayEventHandler handler;

    public RazorpayWebhookController(
            PaymentGateway gateway, RazorpayWebhookParser parser, GatewayEventHandler handler) {
        this.gateway = gateway;
        this.parser = parser;
        this.handler = handler;
    }

    @PostMapping("/api/v1/gateway-webhooks/razorpay/{mode}")
    public Map<String, Boolean> receive(
            @PathVariable("mode") String modeValue,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        Mode mode = Mode.parse(modeValue)
                .orElseThrow(() -> FluxpayException.notFound("NOT_FOUND", "Unknown webhook endpoint"));
        if (!gateway.verifyWebhookSignature(mode, body, signature)) {
            log.warn("Rejected Razorpay webhook with invalid signature for mode {}", mode.value());
            throw FluxpayException.badRequest("INVALID_SIGNATURE", "Webhook signature verification failed");
        }
        switch (parser.parse(body)) {
            case GatewayWebhookEvent.PaymentCaptured captured -> handler.onPaymentCaptured(mode, captured.payment());
            case GatewayWebhookEvent.RefundProcessed refund -> handler.onRefundProcessed(mode, refund.refund());
            case GatewayWebhookEvent.Ignored ignored -> log.debug("Ignoring Razorpay event {}", ignored.type());
        }
        return Map.of("received", true);
    }
}
