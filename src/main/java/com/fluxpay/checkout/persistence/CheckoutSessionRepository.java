package com.fluxpay.checkout.persistence;

import com.fluxpay.checkout.domain.CheckoutSession;
import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CheckoutSessionRepository extends Repository<CheckoutSession, UUID> {

    CheckoutSession save(CheckoutSession session);

    /** Public lookup: the unguessable session id is the capability (spec §8). */
    Optional<CheckoutSession> findById(UUID id);

    Optional<CheckoutSession> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.gatewayOrderId = :orderId")
    Optional<CheckoutSession> lockByGatewayOrderId(@Param("orderId") String orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CheckoutSession s set s.gatewayOrderId = :orderId where s.id = :id and s.gatewayOrderId is null")
    int attachOrder(@Param("id") UUID id, @Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.status = :status and s.expiresAt <= :now order by s.expiresAt")
    List<CheckoutSession> lockDueForExpiry(
            @Param("status") CheckoutStatus status, @Param("now") Instant now, Limit limit);

    @Query("select s from CheckoutSession s where s.gatewayOrderId is not null and s.status in :statuses"
            + " and s.createdAt >= :from and s.createdAt <= :to order by s.createdAt")
    List<CheckoutSession> findReconcilable(
            @Param("statuses") Collection<CheckoutStatus> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Limit limit);
}
