package com.fluxpay.webhook.controller;

import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.webhook.dto.CreateWebhookRequest;
import com.fluxpay.webhook.dto.WebhookEndpointDto;
import com.fluxpay.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<ApiResponse<WebhookEndpointDto>> createEndpoint(@Valid @RequestBody CreateWebhookRequest request) {
        WebhookEndpointDto endpoint = webhookService.createEndpoint(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(endpoint, "Webhook endpoint created successfully"));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<WebhookEndpointDto>>> getActiveEndpoints(@PathVariable UUID merchantId) {
        List<WebhookEndpointDto> endpoints = webhookService.getActiveEndpoints(merchantId);
        return ResponseEntity.ok(ApiResponse.success(endpoints));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateEndpoint(@PathVariable UUID id) {
        webhookService.deactivateEndpoint(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook endpoint deactivated"));
    }
}
