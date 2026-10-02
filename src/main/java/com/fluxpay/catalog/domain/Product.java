package com.fluxpay.catalog.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(
            UUID merchantId,
            Mode mode,
            String name,
            String description,
            String imageUrl,
            long amount,
            String currency,
            Map<String, String> metadata,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.amount = amount;
        this.currency = currency;
        this.type = ProductType.ONE_TIME;
        this.metadata = metadata;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void describe(String description, Instant now) {
        this.description = description;
        this.updatedAt = now;
    }

    public void changeImage(String imageUrl, Instant now) {
        this.imageUrl = imageUrl;
        this.updatedAt = now;
    }

    public void reprice(long amount, Instant now) {
        this.amount = amount;
        this.updatedAt = now;
    }

    public void replaceMetadata(Map<String, String> metadata, Instant now) {
        this.metadata = metadata;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductType getType() {
        return type;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
