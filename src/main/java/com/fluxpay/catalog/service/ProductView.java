package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.catalog.domain.ProductType;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductView(
        UUID id,
        Mode mode,
        String name,
        String description,
        String imageUrl,
        long amount,
        String currency,
        ProductType type,
        Map<String, String> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getMode(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getAmount(),
                product.getCurrency(),
                product.getType(),
                Map.copyOf(product.getMetadata()),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
