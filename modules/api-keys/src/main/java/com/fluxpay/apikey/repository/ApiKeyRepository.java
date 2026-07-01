package com.fluxpay.apikey.repository;

import com.fluxpay.apikey.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByMerchantId(UUID merchantId);
    Optional<ApiKey> findByKeyHash(String keyHash);
}
