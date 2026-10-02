package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.sales.service.CheckoutExpiryService;
import com.fluxpay.sales.service.ReconciliationService;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class JobsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CheckoutExpiryService expiryService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, apiKey, productId, "u_123");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    void should_expire_due_sessions_once_and_emit_checkout_expired() {
        jdbcTemplate.update("UPDATE checkout_sessions SET expires_at = now() - interval '1 minute'");

        assertThat(expiryService.expireDue()).isEqualTo(1);
        assertThat(expiryService.expireDue()).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("EXPIRED");
        assertThat(count("SELECT count(*) FROM events WHERE type = 'CHECKOUT_EXPIRED'"))
                .isEqualTo(1);
    }

    @Test
    void should_not_expire_sessions_that_are_still_valid() {
        assertThat(expiryService.expireDue()).isZero();
    }

    @Test
    void should_record_missed_capture_when_reconciling_old_sessions() throws Exception {
        String orderId = TestMerchants.pay(mockMvc, sessionId);
        paymentGateway.addPayment(new GatewayPayment("pay_r", orderId, 4900, "INR", "captured", "upi", 116));
        jdbcTemplate.update("UPDATE checkout_sessions SET created_at = now() - interval '20 minutes'");

        assertThat(reconciliationService.reconcile()).isEqualTo(1);
        reconciliationService.reconcile();

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("COMPLETED");
    }

    @Test
    void should_skip_recent_sessions_and_ignore_uncaptured_payments() throws Exception {
        String orderId = TestMerchants.pay(mockMvc, sessionId);
        paymentGateway.addPayment(new GatewayPayment("pay_f", orderId, 4900, "INR", "failed", "upi", 0));

        assertThat(reconciliationService.reconcile()).isZero();

        jdbcTemplate.update("UPDATE checkout_sessions SET created_at = now() - interval '20 minutes'");
        reconciliationService.reconcile();
        assertThat(count("SELECT count(*) FROM sales")).isZero();
    }
}
