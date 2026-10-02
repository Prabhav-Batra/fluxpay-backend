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

    private String productId;
    private String apiKey;

    @BeforeEach
    void captureSale() throws Exception {
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        TestMerchants.completeSale(mockMvc, apiKey, productId, 4900, "u_123", "pay_1");
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/gateway-webhooks/razorpay/test")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", TestWebhooks.sign(body))
                .content(body));
    }

    private void deliver(String body) throws Exception {
        send(body).andExpect(status().isOk());
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
    void should_ask_razorpay_to_retry_refund_for_payment_not_yet_recorded() throws Exception {
        send(TestWebhooks.refundProcessed("rfnd_9", "pay_unknown", 2000))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code")
                        .value("PAYMENT_NOT_YET_RECORDED"));

        assertThat(refundTotal()).isZero();
        assertThat(saleStatus()).isEqualTo("PAID");
    }

    @Test
    void should_apply_refund_redelivered_after_late_capture() throws Exception {
        String sessionId = TestMerchants.createCheckoutSession(mockMvc, apiKey, productId, "u_late");
        String orderId = TestMerchants.pay(mockMvc, sessionId);
        String refund = TestWebhooks.refundProcessed("rfnd_late", "pay_late", 1000);

        send(refund).andExpect(status().isConflict());
        deliver(TestWebhooks.paymentCaptured("pay_late", orderId, 4900, 116));
        deliver(refund);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT s.status FROM sales s JOIN payments p ON p.id = s.payment_id"
                                + " WHERE p.gateway_payment_id = 'pay_late'",
                        String.class))
                .isEqualTo("PARTIALLY_REFUNDED");
    }
}
