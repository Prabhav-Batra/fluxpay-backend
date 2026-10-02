package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only product access for merchant backends (API key). */
@RestController
@RequestMapping("/api/v1/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public CursorPage<ProductResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean active) {
        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, startingAfter, limit);
        return productService.list(tenant, query, active).map(ProductResponse::from);
    }

    @GetMapping("/{id}")
    public ProductResponse get(TenantContext tenant, @PathVariable String id) {
        return ProductResponse.from(productService.get(tenant, DashboardProductController.parseId(id)));
    }
}
