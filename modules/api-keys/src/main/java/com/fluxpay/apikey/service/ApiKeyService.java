package com.fluxpay.apikey.service;

import com.fluxpay.apikey.dto.ApiKeyCreateRequest;
import com.fluxpay.apikey.dto.ApiKeyDto;
import com.fluxpay.apikey.entity.ApiKey;
import com.fluxpay.apikey.repository.ApiKeyRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    public String createApiKey(ApiKeyCreateRequest request) {
        String rawKey = generateRawKey(request.getMode());
        String keyHash = hashKey(rawKey);
        String keyPrefix = rawKey.substring(0, 12) + "***";

        ApiKey apiKey = ApiKey.builder()
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .mode(request.getMode().toUpperCase())
                .merchantId(request.getMerchantId())
                .active(true)
                .build();

        apiKeyRepository.save(apiKey);
        
        // The raw key is returned ONLY once
        return rawKey;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> getApiKeysByMerchant(UUID merchantId) {
        return apiKeyRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeApiKey(UUID id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id.toString()));
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
    }

    private String generateRawKey(String mode) {
        String prefix = mode.equalsIgnoreCase("TEST") ? "sk_test_" : "sk_live_";
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes());
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }

    private ApiKeyDto mapToDto(ApiKey apiKey) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .keyPrefix(apiKey.getKeyPrefix())
                .mode(apiKey.getMode())
                .merchantId(apiKey.getMerchantId())
                .active(apiKey.isActive())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }
}
