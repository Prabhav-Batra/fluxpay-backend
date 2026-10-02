package com.fluxpay.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class IdempotencyServiceIntegrationTest extends AbstractIntegrationTest {

    record Request(String productId, long amount) {}

    record Response(String id, long amount) {}

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TenantContext tenant;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        String merchantId =
                TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com").merchantId();
        tenant = new TenantContext(PublicId.parse(IdPrefix.MERCHANT, merchantId).orElseThrow(), Mode.TEST);
    }

    private Response run(String key, Request request) {
        return idempotency.execute(
                tenant, key, request, Response.class, () -> new Response("cs_" + calls.incrementAndGet(), 4900));
    }

    @Test
    void should_return_stored_response_without_rerunning_when_key_and_request_repeat() {
        Response first = run("key-1", new Request("prod_1", 4900));
        Response second = run("key-1", new Request("prod_1", 4900));

        assertThat(second).isEqualTo(first);
        assertThat(calls).hasValue(1);
    }

    @Test
    void should_reject_same_key_with_different_request() {
        run("key-1", new Request("prod_1", 4900));

        assertThatThrownBy(() -> run("key-1", new Request("prod_2", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(calls).hasValue(1);
    }

    @Test
    void should_reject_while_first_request_is_in_progress() {
        Request request = new Request("prod_1", 4900);
        jdbcTemplate.update(
                "INSERT INTO idempotency_keys (merchant_id, mode, idempotency_key, request_hash, created_at)"
                        + " VALUES (?, 'TEST', 'key-1', ?, now())",
                tenant.merchantId(),
                IdempotencyService.sha256Hex("{\"product_id\":\"prod_1\",\"amount\":4900}"));

        assertThatThrownBy(() -> run("key-1", request))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");
    }

    @Test
    void should_release_key_when_action_fails() {
        Request request = new Request("prod_1", 4900);
        assertThatThrownBy(() -> idempotency.execute(tenant, "key-1", request, Response.class, () -> {
                    throw FluxpayException.conflict("PRODUCT_INACTIVE", "archived");
                }))
                .isInstanceOf(FluxpayException.class);

        assertThat(run("key-1", request).id()).isEqualTo("cs_1");
    }

    @Test
    void should_run_every_time_without_key_and_reject_invalid_keys() {
        run(null, new Request("prod_1", 4900));
        run(null, new Request("prod_1", 4900));

        assertThat(calls).hasValue(2);
        assertThatThrownBy(() -> run("x".repeat(256), new Request("prod_1", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThatThrownBy(() -> run(" ", new Request("prod_1", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void should_delete_keys_older_than_cutoff() {
        run("old", new Request("prod_1", 4900));
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET created_at = ?", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")));
        run("new", new Request("prod_1", 4900));

        int deleted = idempotency.deleteOlderThan(Instant.parse("2021-01-01T00:00:00Z"));

        assertThat(deleted).isEqualTo(1);
    }
}
