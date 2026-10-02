package com.fluxpay.events.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.Event;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends Repository<Event, UUID> {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    Optional<Event> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Query("select e from Event e where e.merchantId = :merchantId and e.mode = :mode and e.id < :before"
            + " order by e.id desc")
    List<Event> page(
            @Param("merchantId") UUID merchantId, @Param("mode") Mode mode, @Param("before") UUID before, Limit limit);
}
