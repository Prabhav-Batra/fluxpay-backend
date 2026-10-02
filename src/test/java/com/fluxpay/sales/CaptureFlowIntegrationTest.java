package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class CaptureFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK = "/api/v1/gateway-webhooks/razorpay/test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;
    private String productId;
    private String apiKey;
    private String sessionId;
    private String orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, apiKey, productId, "u_123");
        orderId = TestMerchants.pay(mockMvc, sessionId);
    }

    private ResultActions deliver(String path, String body, String signature) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("X-Razorpay-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    private ResultActions deliver(String body) throws Exception {
        return deliver(WEBHOOK, body, TestWebhooks.sign(body));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    void should_record_sale_ledger_and_event_when_payment_is_captured() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        mockMvc.perform(get("/api/v1/checkout_sessions/" + sessionId).header("Authorization", "Bearer " + apiKey))
                .andExpect(jsonPath("$.status").value("completed"));
        assertThat(jdbcTemplate.queryForObject("SELECT customer_ref FROM sales", String.class))
                .isEqualTo("u_123");
        assertThat(jdbcTemplate.queryForList("SELECT amount FROM ledger_entries ORDER BY amount DESC", Long.class))
                .containsExactly(4900L, -116L, -245L);
        assertThat(jdbcTemplate.queryForObject("SELECT data->>'customer_ref' FROM events", String.class))
                .isEqualTo("u_123");
        assertThat(jdbcTemplate.queryForObject("SELECT data->'metadata'->>'order' FROM events", String.class))
                .isEqualTo("42");
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(jsonPath("$.success_url").value("https://jextter.com/paid"));
    }

    @Test
    void should_record_exactly_once_when_webhook_is_delivered_twice() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);

        deliver(body).andExpect(status().isOk());
        deliver(body).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger_entries")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM events")).isEqualTo(1);
    }

    @Test
    void should_record_exactly_once_when_duplicates_arrive_concurrently() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Callable<Integer> delivery =
                () -> deliver(body).andReturn().getResponse().getStatus();

        pool.invokeAll(List.of(delivery, delivery, delivery));
        pool.shutdown();

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payments")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM events")).isEqualTo(1);
    }

    @Test
    void should_still_record_sale_when_payment_arrives_after_expiry() throws Exception {
        jdbcTemplate.update(
                "UPDATE checkout_sessions SET status = 'EXPIRED', expires_at = now() - interval '5 minutes'");

        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
    }

    @Test
    void should_flag_payment_and_skip_sale_when_amount_differs_from_snapshot() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 100, 0)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payments", String.class))
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("OPEN");
    }

    @Test
    void should_record_snapshot_price_after_product_is_repriced() throws Exception {
        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9900}"));

        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM sales", Long.class))
                .isEqualTo(4900L);
    }

    @Test
    void should_flag_second_payment_for_completed_session_as_duplicate() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116));

        deliver(TestWebhooks.paymentCaptured("pay_2", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE gateway_payment_id = 'pay_2'", String.class))
                .isEqualTo("DUPLICATE");
    }

    @Test
    void should_reject_missing_or_wrong_signature_and_unconfigured_mode() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);

        deliver(WEBHOOK, body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
        deliver(WEBHOOK, body, "deadbeef").andExpect(status().isBadRequest());
        deliver("/api/v1/gateway-webhooks/razorpay/live", body, TestWebhooks.sign(body))
                .andExpect(status().isBadRequest());
        deliver("/api/v1/gateway-webhooks/razorpay/prod", body, TestWebhooks.sign(body))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM sales")).isZero();
    }

    @Test
    void should_acknowledge_unknown_orders_and_ignored_event_types() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_9", "order_unknown", 4900, 116))
                .andExpect(status().isOk());
        deliver("{\"event\":\"order.paid\",\"payload\":{}}").andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM payments")).isZero();
    }
}
