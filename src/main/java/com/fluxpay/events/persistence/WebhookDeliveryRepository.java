package com.fluxpay.events.persistence;

import com.fluxpay.events.domain.DeliveryStatus;
import com.fluxpay.events.domain.WebhookDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends Repository<WebhookDelivery, UUID> {

    WebhookDelivery save(WebhookDelivery delivery);

    Optional<WebhookDelivery> findById(UUID id);

    List<WebhookDelivery> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    /** Row-locks due deliveries; SKIP LOCKED lets concurrent dispatchers take disjoint batches. */
    @Query(
            value = "SELECT * FROM webhook_deliveries WHERE status = :status AND next_attempt_at <= :now"
                    + " ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<WebhookDelivery> lockDue(@Param("status") String status, @Param("now") Instant now, @Param("limit") int limit);

    default List<WebhookDelivery> lockDue(Instant now, int limit) {
        return lockDue(DeliveryStatus.PENDING.name(), now, limit);
    }
}
