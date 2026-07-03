package com.fluxpay.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, name = "merchant_id")
    private UUID merchantId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "product_type")
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private BillingCycle billingCycle;

    @Column(name = "trial_period_days")
    private Integer trialPeriodDays;

    @Column(name = "grace_period_days")
    private Integer gracePeriodDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "renewal_rules")
    private Map<String, Object> renewalRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_rules")
    private Map<String, Object> cancellationRules;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> benefits;

    @Column(name = "base_credits")
    private Integer baseCredits;

    @Column(name = "bonus_credits")
    private Integer bonusCredits;

    @Column(name = "purchase_limit")
    private Integer purchaseLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductVisibility visibility;

    @Column(name = "display_image")
    private String displayImage;

    @Column(name = "hosted_checkout_url")
    private String hostedCheckoutUrl;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
