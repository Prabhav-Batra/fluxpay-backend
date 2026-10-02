package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.catalog.persistence.ProductRepository;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.validation.Metadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    static final String SUPPORTED_CURRENCY = "INR";

    private final ProductRepository products;
    private final Clock clock;

    public ProductServiceImpl(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProductView create(TenantContext tenant, NewProduct product) {
        String currency = product.currency() == null ? SUPPORTED_CURRENCY : product.currency();
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw FluxpayException.validation("currency", "UNSUPPORTED_CURRENCY", "Only INR is supported");
        }
        Product saved = products.save(new Product(
                tenant.merchantId(),
                tenant.mode(),
                product.name().trim(),
                blankToNull(product.description()),
                blankToNull(product.imageUrl()),
                product.amount(),
                currency,
                Metadata.validated(product.metadata()),
                Instant.now(clock)));
        return ProductView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView get(TenantContext tenant, UUID productId) {
        return ProductView.from(find(tenant, productId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ProductView> list(TenantContext tenant, PageQuery query, Boolean active) {
        List<Boolean> actives = active == null ? List.of(true, false) : List.of(active);
        List<Product> rows =
                products.page(tenant.merchantId(), tenant.mode(), query.before(), actives, Limit.of(query.fetchSize()));
        return CursorPage.from(rows, query, ProductView::from, view -> PublicId.of(IdPrefix.PRODUCT, view.id()));
    }

    @Override
    @Transactional
    public ProductView update(TenantContext tenant, UUID productId, ProductChanges changes) {
        Product product = find(tenant, productId);
        Instant now = Instant.now(clock);
        if (changes.name() != null) {
            product.rename(changes.name().trim(), now);
        }
        if (changes.description() != null) {
            product.describe(blankToNull(changes.description()), now);
        }
        if (changes.imageUrl() != null) {
            product.changeImage(blankToNull(changes.imageUrl()), now);
        }
        if (changes.amount() != null) {
            product.reprice(changes.amount(), now);
        }
        if (changes.metadata() != null) {
            product.replaceMetadata(Metadata.validated(changes.metadata()), now);
        }
        if (changes.active() != null) {
            product.setActive(changes.active(), now);
        }
        return ProductView.from(product);
    }

    private Product find(TenantContext tenant, UUID productId) {
        return products.findByIdAndMerchantIdAndMode(productId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
