package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class RefundFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void captureSale() throws Exception {
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        TestMerchants.completeSale(mockMvc, apiKey, productId, 4900, "u_123", "pay_1");
    }

    private void deliver(String body) throws Exception {
        mockMvc.perform(post("/api/v1/gateway-webhooks/razorpay/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", TestWebhooks.sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private String saleStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM sales", String.class);
    }

    private long refundTotal() {
        return jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE type = 'REFUND'", Long.class);
    }

    @Test
    void should_mark_partial_then_full_refund_with_ledger_entries_and_events() throws Exception {
        deliver(TestWebhooks.refundProcessed("rfnd_1", "pay_1", 2000));

        assertThat(saleStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(refundTotal()).isEqualTo(-2000);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT data->>'amount_refunded' FROM events WHERE type = 'SALE_REFUNDED'", String.class))
                .isEqualTo("2000");

        deliver(TestWebhooks.refundProcessed("rfnd_2", "pay_1", 2900));

        assertThat(saleStatus()).isEqualTo("REFUNDED");
        assertThat(refundTotal()).isEqualTo(-4900);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM events WHERE type = 'SALE_REFUNDED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void should_apply_each_refund_once_when_webhook_repeats() throws Exception {
        String body = TestWebhooks.refundProcessed("rfnd_1", "pay_1", 2000);

        deliver(body);
        deliver(body);

        assertThat(refundTotal()).isEqualTo(-2000);
        assertThat(jdbcTemplate.queryForObject("SELECT refunded_amount FROM sales", Long.class))
                .isEqualTo(2000L);
    }

    @Test
    void should_ignore_refund_for_unknown_payment() throws Exception {
        deliver(TestWebhooks.refundProcessed("rfnd_9", "pay_unknown", 2000));

        assertThat(refundTotal()).isZero();
        assertThat(saleStatus()).isEqualTo("PAID");
    }
}
