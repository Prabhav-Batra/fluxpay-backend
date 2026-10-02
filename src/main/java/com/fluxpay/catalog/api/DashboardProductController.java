package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.NewProduct;
import com.fluxpay.catalog.service.ProductChanges;
import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/products")
public class DashboardProductController {

    private final ProductService productService;

    public DashboardProductController(ProductService productService) {
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(TenantContext tenant, @Valid @RequestBody CreateProductRequest body) {
        NewProduct product = new NewProduct(
                body.name(), body.description(), body.imageUrl(), body.amount(), body.currency(), body.metadata());
        return ProductResponse.from(productService.create(tenant, product));
    }

    @GetMapping("/{id}")
    public ProductResponse get(TenantContext tenant, @PathVariable String id) {
        return ProductResponse.from(productService.get(tenant, parseId(id)));
    }

    @PatchMapping("/{id}")
    public ProductResponse update(
            TenantContext tenant, @PathVariable String id, @Valid @RequestBody UpdateProductRequest body) {
        ProductChanges changes = new ProductChanges(
                body.name(), body.description(), body.imageUrl(), body.amount(), body.metadata(), body.active());
        return ProductResponse.from(productService.update(tenant, parseId(id), changes));
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.PRODUCT, id, "PRODUCT_NOT_FOUND", "Product not found");
    }
}
