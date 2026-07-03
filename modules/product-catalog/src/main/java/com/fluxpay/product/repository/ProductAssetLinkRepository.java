package com.fluxpay.product.repository;

import com.fluxpay.product.entity.ProductAssetLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductAssetLinkRepository extends JpaRepository<ProductAssetLink, UUID> {
    List<ProductAssetLink> findByProductId(UUID productId);
    void deleteByProductId(UUID productId);
}
