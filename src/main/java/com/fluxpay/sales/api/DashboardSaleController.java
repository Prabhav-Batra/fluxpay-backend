package com.fluxpay.sales.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.sales.domain.SaleStatus;
import com.fluxpay.sales.service.SaleFilter;
import com.fluxpay.sales.service.SalesQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/sales")
public class DashboardSaleController {

    private final SalesQueryService salesQueryService;

    public DashboardSaleController(SalesQueryService salesQueryService) {
        this.salesQueryService = salesQueryService;
    }

    @GetMapping
    public CursorPage<SaleResponse> list(
            TenantContext tenant,
            @RequestParam(name = "customer_ref", required = false) String customerRef,
            @RequestParam(name = "product_id", required = false) String productId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.SALE, startingAfter, limit);
        SaleStatus parsedStatus = status == null
                ? null
                : SaleStatus.parse(status)
                        .orElseThrow(() -> FluxpayException.badRequest(
                                "INVALID_STATUS", "status must be paid, partially_refunded or refunded"));
        SaleFilter filter = new SaleFilter(customerRef, SaleApiController.parseProductFilter(productId), parsedStatus);
        return salesQueryService.list(tenant, query, filter).map(SaleResponse::from);
    }

    @GetMapping("/{id}")
    public SaleDetailResponse get(TenantContext tenant, @PathVariable String id) {
        return SaleDetailResponse.from(salesQueryService.get(tenant, SaleApiController.parseId(id)));
    }
}
