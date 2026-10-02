package com.fluxpay.catalog.api;

import com.fluxpay.catalog.domain.ProductType;
import com.fluxpay.catalog.service.ProductView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
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

    public static ProductResponse from(ProductView view) {
        return new ProductResponse(
                PublicId.of(IdPrefix.PRODUCT, view.id()),
                view.mode(),
                view.name(),
                view.description(),
                view.imageUrl(),
                view.amount(),
                view.currency(),
                view.type(),
                view.metadata(),
                view.active(),
                view.createdAt(),
                view.updatedAt());
    }
}
