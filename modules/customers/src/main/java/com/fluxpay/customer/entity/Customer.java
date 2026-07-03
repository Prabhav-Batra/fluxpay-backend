package com.fluxpay.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private String email;

    @Column
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_spent", nullable = false)
    private BigDecimal totalSpent;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant joinDate;

    @UpdateTimestamp
    private Instant updatedAt;
}
