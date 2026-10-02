package com.fluxpay.webhook.service;

import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.webhook.dto.CreateWebhookRequest;
import com.fluxpay.webhook.dto.WebhookEndpointDto;
import com.fluxpay.webhook.entity.WebhookEndpoint;
import com.fluxpay.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEndpointRepository webhookEndpointRepository;

    @Transactional
    public WebhookEndpointDto createEndpoint(CreateWebhookRequest request) {
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .merchantId(request.getMerchantId())
                .url(request.getUrl())
                .secretKey(generateSecretKey())
                .active(true)
                .build();

        endpoint = webhookEndpointRepository.save(endpoint);
        return mapToDto(endpoint);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointDto> getActiveEndpoints(UUID merchantId) {
        return webhookEndpointRepository.findByMerchantIdAndActiveTrue(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateEndpoint(UUID id, UUID callerMerchantId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findById(id)
                .filter(e -> e.getMerchantId().equals(callerMerchantId))
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id.toString()));
        endpoint.setActive(false);
        webhookEndpointRepository.save(endpoint);
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateSecretKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private WebhookEndpointDto mapToDto(WebhookEndpoint endpoint) {
        return WebhookEndpointDto.builder()
                .id(endpoint.getId())
                .merchantId(endpoint.getMerchantId())
                .url(endpoint.getUrl())
                .secretKey(endpoint.getSecretKey())
                .active(endpoint.isActive())
                .createdAt(endpoint.getCreatedAt())
                .build();
    }
}
