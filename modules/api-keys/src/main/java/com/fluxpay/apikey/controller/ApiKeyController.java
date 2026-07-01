package com.fluxpay.apikey.controller;

import com.fluxpay.apikey.dto.ApiKeyCreateRequest;
import com.fluxpay.apikey.dto.ApiKeyDto;
import com.fluxpay.apikey.service.ApiKeyService;
import com.fluxpay.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createApiKey(@Valid @RequestBody ApiKeyCreateRequest request) {
        String rawKey = apiKeyService.createApiKey(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("rawKey", rawKey), "API Key created successfully. Store this key, it will not be shown again."));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<ApiKeyDto>>> getApiKeysByMerchant(@PathVariable UUID merchantId) {
        List<ApiKeyDto> keys = apiKeyService.getApiKeysByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID id) {
        apiKeyService.revokeApiKey(id);
        return ResponseEntity.ok(ApiResponse.success(null, "API Key revoked successfully"));
    }
}
