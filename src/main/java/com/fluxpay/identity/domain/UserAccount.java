package com.fluxpay.identity.domain;

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
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    private UserAccount(String email, String passwordHash, Role role, UUID merchantId, Instant now) {
        this.id = UuidV7.generate();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.merchantId = merchantId;
        this.createdAt = now;
    }

    public static UserAccount merchantOwner(String email, String passwordHash, UUID merchantId, Instant now) {
        return new UserAccount(email, passwordHash, Role.MERCHANT_OWNER, merchantId, now);
    }

    public static UserAccount platformAdmin(String email, String passwordHash, Instant now) {
        return new UserAccount(email, passwordHash, Role.PLATFORM_ADMIN, null, now);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}
