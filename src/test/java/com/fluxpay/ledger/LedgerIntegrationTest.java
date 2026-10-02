package com.fluxpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.service.Balance;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.ledger.service.SaleEntries;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private SignedIn owner;
    private TenantContext tenant;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        tenant = new TenantContext(
                PublicId.parse(IdPrefix.MERCHANT, owner.merchantId()).orElseThrow(), Mode.TEST);
    }

    @Test
    void should_record_gross_platform_fee_and_gateway_fee_for_a_sale() {
        SaleEntries entries = transactionTemplate.execute(
                status -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 116));

        assertThat(entries.platformFee()).isEqualTo(245);
        assertThat(entries.net()).isEqualTo(4900 - 245 - 116);
        Balance balance = ledgerService.balance(tenant);
        assertThat(balance.grossSales()).isEqualTo(4900);
        assertThat(balance.platformFees()).isEqualTo(-245);
        assertThat(balance.gatewayFees()).isEqualTo(-116);
        assertThat(balance.available()).isEqualTo(4539);
    }

    @Test
    void should_record_refund_once_per_gateway_refund_id() {
        UUID saleId = UuidV7.generate();
        transactionTemplate.executeWithoutResult(status -> ledgerService.recordSale(tenant, saleId, 4900, "INR", 0));

        Boolean first = transactionTemplate.execute(
                status -> ledgerService.recordRefund(tenant, saleId, "rfnd_1", 4900, "INR"));
        Boolean second = transactionTemplate.execute(
                status -> ledgerService.recordRefund(tenant, saleId, "rfnd_1", 4900, "INR"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(ledgerService.balance(tenant).refunds()).isEqualTo(-4900);
    }

    @Test
    void should_require_a_transaction_for_writes() {
        assertThatThrownBy(() -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 0))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_expose_balance_and_entries_on_dashboard_per_mode() throws Exception {
        transactionTemplate.executeWithoutResult(
                status -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 116));

        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(owner.session()))
                .andExpect(jsonPath("$.gross_sales").value(4900))
                .andExpect(jsonPath("$.available").value(4539))
                .andExpect(jsonPath("$.currency").value("INR"));
        mockMvc.perform(get("/api/v1/dashboard/ledger_entries").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(org.hamcrest.Matchers.startsWith("le_")));
        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(owner.session()).header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.available").value(0));
    }
}
