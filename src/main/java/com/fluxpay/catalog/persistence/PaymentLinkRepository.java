package com.fluxpay.catalog.persistence;

import com.fluxpay.catalog.domain.PaymentLink;
import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PaymentLinkRepository extends Repository<PaymentLink, UUID> {

    PaymentLink save(PaymentLink link);

    Optional<PaymentLink> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    /** Public lookup: the slug is the capability. */
    Optional<PaymentLink> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    @Query("select l from PaymentLink l where l.merchantId = :merchantId and l.mode = :mode and l.id < :before"
            + " order by l.id desc")
    List<PaymentLink> page(
            @Param("merchantId") UUID merchantId, @Param("mode") Mode mode, @Param("before") UUID before, Limit limit);
}
