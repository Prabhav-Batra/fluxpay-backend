package com.fluxpay.merchants.domain;

import com.fluxpay.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "brand_color")
    private String brandColor;

    @Column(name = "platform_fee_bps", nullable = false)
    private int platformFeeBps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {}

    public Merchant(String businessName, String slug, int platformFeeBps, Instant now) {
        this.id = UuidV7.generate();
        this.businessName = businessName;
        this.slug = slug;
        this.platformFeeBps = platformFeeBps;
        this.status = MerchantStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String businessName, Instant now) {
        this.businessName = businessName;
        this.updatedAt = now;
    }

    public void changeBranding(String logoUrl, String brandColor, Instant now) {
        this.logoUrl = logoUrl;
        this.brandColor = brandColor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getSlug() {
        return slug;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getBrandColor() {
        return brandColor;
    }

    public int getPlatformFeeBps() {
        return platformFeeBps;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
