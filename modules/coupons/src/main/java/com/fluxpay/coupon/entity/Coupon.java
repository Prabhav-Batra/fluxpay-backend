package com.fluxpay.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "discount_type")
    private DiscountType discountType;

    @Column(nullable = false, name = "discount_value")
    private BigDecimal discountValue;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(nullable = false, name = "times_used")
    @Builder.Default
    private Integer timesUsed = 0;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
    
    public void incrementUsage() {
        this.timesUsed++;
    }
}
