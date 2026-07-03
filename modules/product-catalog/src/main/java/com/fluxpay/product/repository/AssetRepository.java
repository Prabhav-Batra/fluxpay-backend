package com.fluxpay.product.repository;

import com.fluxpay.product.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByMerchantId(UUID merchantId);
    Optional<Asset> findByInternalKeyAndMerchantId(String internalKey, UUID merchantId);
}
