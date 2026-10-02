package com.fluxpay.ledger.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.Payout;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends Repository<Payout, UUID> {

    Payout save(Payout payout);

    boolean existsByMerchantIdAndModeAndReference(UUID merchantId, Mode mode, String reference);

    @Query("select p from Payout p where p.merchantId = :merchantId and p.mode = :mode and p.id < :before"
            + " order by p.id desc")
    List<Payout> page(
            @Param("merchantId") UUID merchantId, @Param("mode") Mode mode, @Param("before") UUID before, Limit limit);

    /** Serialises payouts per merchant and mode so two payouts cannot both pass the balance check. */
    @Query(
            value =
                    "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('payout:' || :merchantId || ':' || :mode))) l",
            nativeQuery = true)
    Integer lockBalance(@Param("merchantId") String merchantId, @Param("mode") String mode);
}
