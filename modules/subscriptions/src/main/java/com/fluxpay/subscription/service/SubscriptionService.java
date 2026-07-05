package com.fluxpay.subscription.service;

import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.shared.exception.BusinessException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.subscription.dto.SubscriptionCreateRequest;
import com.fluxpay.subscription.dto.SubscriptionDto;
import com.fluxpay.subscription.entity.Subscription;
import com.fluxpay.subscription.entity.SubscriptionStatus;
import com.fluxpay.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import com.fluxpay.shared.utils.TraceLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ProductService productService;

    @Transactional
    public SubscriptionDto createSubscription(SubscriptionCreateRequest request) {
        ProductDto product = productService.getProduct(request.getProductId());
        
        if (!product.getMerchantId().equals(request.getMerchantId())) {
            throw new BusinessException("Product does not belong to the merchant", "INVALID_PRODUCT");
        }

        // For simplicity, a standard 30-day billing cycle is hardcoded here.
        // In a real system, this would be read from the Product's metadata (e.g. interval=month)
        Instant now = Instant.now();
        Instant periodEnd = now.plus(30, ChronoUnit.DAYS);

        Subscription subscription = Subscription.builder()
                .merchantId(request.getMerchantId())
                .customerEmail(request.getCustomerEmail())
                .productId(product.getId())
                .status(SubscriptionStatus.INCOMPLETE) // Awaits first payment
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .cancelAtPeriodEnd(false)
                .build();

        subscription = subscriptionRepository.save(subscription);

        TraceLogger.emit("CREATE_SUBSCRIPTION", 1.0, Map.of(
            "subscriptionId", subscription.getId(),
            "merchantId", subscription.getMerchantId(),
            "productId", subscription.getProductId()
        ));

        return mapToDto(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getSubscription(UUID id) {
        return subscriptionRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getCustomerSubscriptions(UUID merchantId, String customerEmail) {
        return subscriptionRepository.findByMerchantIdAndCustomerEmail(merchantId, customerEmail).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getAllSubscriptions(UUID merchantId) {
        return subscriptionRepository.findByMerchantId(merchantId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SubscriptionDto cancelSubscription(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id.toString()));

        subscription.setCancelAtPeriodEnd(true);
        // It remains ACTIVE until currentPeriodEnd is reached by a background cron job
        subscription = subscriptionRepository.save(subscription);
        
        TraceLogger.emit("CANCEL_SUBSCRIPTION", 1.0, Map.of(
            "subscriptionId", subscription.getId(),
            "merchantId", subscription.getMerchantId()
        ));
        
        return mapToDto(subscription);
    }

    private SubscriptionDto mapToDto(Subscription subscription) {
        return SubscriptionDto.builder()
                .id(subscription.getId())
                .merchantId(subscription.getMerchantId())
                .customerEmail(subscription.getCustomerEmail())
                .productId(subscription.getProductId())
                .status(subscription.getStatus())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(subscription.isCancelAtPeriodEnd())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
