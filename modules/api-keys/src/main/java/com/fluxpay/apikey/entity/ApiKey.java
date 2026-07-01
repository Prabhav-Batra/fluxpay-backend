package com.fluxpay.apikey.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private String keyPrefix; // Store only the prefix (e.g. "sk_test_***") for identification

    @Column(nullable = false)
    private String mode; // e.g. "LIVE" or "TEST"

    @Column(nullable = false, name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
