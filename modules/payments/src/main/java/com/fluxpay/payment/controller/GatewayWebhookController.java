package com.fluxpay.payment.controller;

import com.fluxpay.payment.service.GatewayWebhookService;
import com.fluxpay.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/gateways")
@RequiredArgsConstructor
public class GatewayWebhookController {

    private final GatewayWebhookService gatewayWebhookService;

    @PostMapping("/cashfree/webhook")
    public ResponseEntity<ApiResponse<Void>> handleCashfreeWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody String rawPayload) {
        
        log.info("Received Cashfree webhook request. Headers: {}", headers);
        
        // Cashfree usually sends a signature header. 
        // Example: String signature = headers.get("x-webhook-signature");
        // String timestamp = headers.get("x-webhook-timestamp");
        // String dataToVerify = timestamp + rawPayload;
        // cashfreeSignatureValidator.verify(signature, dataToVerify, cashfreeSecretKey);

        gatewayWebhookService.processCashfreeWebhook(rawPayload);

        return ResponseEntity.ok(ApiResponse.success(null, "Webhook processed successfully"));
    }
}
