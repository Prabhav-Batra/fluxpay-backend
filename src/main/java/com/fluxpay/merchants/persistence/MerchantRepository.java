package com.fluxpay.merchants.persistence;

import com.fluxpay.merchants.domain.Merchant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsBySlug(String slug);

    /** Serializes slug selection for one base slug within the current transaction. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('slug:' || :slug))) AS l", nativeQuery = true)
    Integer lockSlug(@Param("slug") String slug);
}
