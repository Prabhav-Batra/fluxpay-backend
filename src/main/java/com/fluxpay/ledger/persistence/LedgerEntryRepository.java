package com.fluxpay.ledger.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    boolean existsByTypeAndReference(LedgerEntryType type, String reference);

    @Query("select e from LedgerEntry e where e.merchantId = :merchantId and e.mode = :mode and e.id < :before"
            + " order by e.id desc")
    List<LedgerEntry> page(
            @Param("merchantId") UUID merchantId, @Param("mode") Mode mode, @Param("before") UUID before, Limit limit);

    @Query("select e.type, sum(e.amount) from LedgerEntry e where e.merchantId = :merchantId and e.mode = :mode"
            + " group by e.type")
    List<Object[]> totalsByType(@Param("merchantId") UUID merchantId, @Param("mode") Mode mode);
}
