package com.fluxpay.catalog.persistence;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.common.tenant.Mode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped finders only. */
public interface ProductRepository extends Repository<Product, UUID> {

    Product save(Product product);

    Optional<Product> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Query("select p from Product p where p.merchantId = :merchantId and p.mode = :mode and p.id < :before"
            + " and p.active in :actives order by p.id desc")
    List<Product> page(
            @Param("merchantId") UUID merchantId,
            @Param("mode") Mode mode,
            @Param("before") UUID before,
            @Param("actives") Collection<Boolean> actives,
            Limit limit);
}
