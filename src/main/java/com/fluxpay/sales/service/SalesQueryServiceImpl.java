package com.fluxpay.sales.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.payments.service.PaymentRecordService;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.persistence.SaleRepository;
import com.fluxpay.sales.persistence.SaleSpecifications;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesQueryServiceImpl implements SalesQueryService {

    private final SaleRepository sales;
    private final PaymentRecordService paymentRecords;

    public SalesQueryServiceImpl(SaleRepository sales, PaymentRecordService paymentRecords) {
        this.sales = sales;
        this.paymentRecords = paymentRecords;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<SaleView> list(TenantContext tenant, PageQuery query, SaleFilter filter) {
        List<Specification<Sale>> specs = new ArrayList<>();
        specs.add(SaleSpecifications.forTenant(tenant.merchantId(), tenant.mode()));
        specs.add(SaleSpecifications.idBefore(query.before()));
        if (filter.customerRef() != null) {
            specs.add(SaleSpecifications.customerRef(filter.customerRef()));
        }
        if (filter.productId() != null) {
            specs.add(SaleSpecifications.productId(filter.productId()));
        }
        if (filter.status() != null) {
            specs.add(SaleSpecifications.status(filter.status()));
        }
        List<Sale> rows =
                sales.findBy(Specification.allOf(specs), fluent -> fluent.sortBy(Sort.by(Sort.Direction.DESC, "id"))
                        .limit(query.fetchSize())
                        .all());
        return CursorPage.from(rows, query, SaleView::from, view -> PublicId.of(IdPrefix.SALE, view.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleDetailView get(TenantContext tenant, UUID saleId) {
        Sale sale = sales.findByIdAndMerchantIdAndMode(saleId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("SALE_NOT_FOUND", "Sale not found"));
        RecordedPayment payment = paymentRecords.findById(sale.getPaymentId()).orElseThrow();
        return new SaleDetailView(SaleView.from(sale), payment);
    }
}
