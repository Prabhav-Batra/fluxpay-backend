package com.fluxpay.sales.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.sales.service.SaleFilter;
import com.fluxpay.sales.service.SalesQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets a merchant backend check what a customer bought (spec §4.3). */
@RestController
@RequestMapping("/api/v1/sales")
public class SaleApiController {

    private final SalesQueryService salesQueryService;

    public SaleApiController(SalesQueryService salesQueryService) {
        this.salesQueryService = salesQueryService;
    }

    @GetMapping
    public CursorPage<SaleResponse> list(
            TenantContext tenant,
            @RequestParam(name = "customer_ref", required = false) String customerRef,
            @RequestParam(name = "product_id", required = false) String productId,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.SALE, startingAfter, limit);
        SaleFilter filter = new SaleFilter(customerRef, parseProductFilter(productId), null);
        return salesQueryService.list(tenant, query, filter).map(SaleResponse::from);
    }

    @GetMapping("/{id}")
    public SaleResponse get(TenantContext tenant, @PathVariable String id) {
        return SaleResponse.from(salesQueryService.get(tenant, parseId(id)).sale());
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.SALE, id, "SALE_NOT_FOUND", "Sale not found");
    }

    static UUID parseProductFilter(String productId) {
        if (productId == null) {
            return null;
        }
        return PublicId.parse(IdPrefix.PRODUCT, productId)
                .orElseThrow(() -> FluxpayException.badRequest("INVALID_PRODUCT_ID", "product_id is not a prod_ id"));
    }
}
