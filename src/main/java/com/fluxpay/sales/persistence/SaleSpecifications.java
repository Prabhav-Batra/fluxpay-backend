package com.fluxpay.sales.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.domain.SaleStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class SaleSpecifications {

    private SaleSpecifications() {}

    public static Specification<Sale> forTenant(UUID merchantId, Mode mode) {
        return (root, query, cb) ->
                cb.and(cb.equal(root.get("merchantId"), merchantId), cb.equal(root.get("mode"), mode));
    }

    public static Specification<Sale> idBefore(UUID before) {
        return (root, query, cb) -> cb.lessThan(root.<UUID>get("id"), before);
    }

    public static Specification<Sale> customerRef(String customerRef) {
        return (root, query, cb) -> cb.equal(root.get("customerRef"), customerRef);
    }

    public static Specification<Sale> productId(UUID productId) {
        return (root, query, cb) -> cb.equal(root.get("productId"), productId);
    }

    public static Specification<Sale> status(SaleStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
