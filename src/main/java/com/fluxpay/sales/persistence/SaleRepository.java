package com.fluxpay.sales.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends Repository<Sale, UUID>, JpaSpecificationExecutor<Sale> {

    Sale save(Sale sale);

    Optional<Sale> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.paymentId = :paymentId")
    Optional<Sale> lockByPaymentId(@Param("paymentId") UUID paymentId);
}
