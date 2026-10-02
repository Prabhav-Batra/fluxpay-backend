package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.service.WebhookDispatcher;
import com.fluxpay.events.service.WebhookSigner;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookDispatchIntegrationTest extends AbstractIntegrationTest {

    record Received(String body, String signature, String eventType) {}

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private volatile long responseDelayMillis;
    private SignedIn owner;
    private String endpointId;
    private String secret;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hooks", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(new Received(
                    new String(body),
                    exchange.getRequestHeaders().getFirst("FluxPay-Signature"),
                    exchange.getRequestHeaders().getFirst("FluxPay-Event-Type")));
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(responseCode.get(), -1);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String created = mockMvc.perform(post("/api/v1/dashboard/webhook_endpoints")
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost:%d/hooks\"}"
                                .formatted(server.getAddress().getPort())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        endpointId = JsonPath.read(created, "$.id");
        secret = JsonPath.read(created, "$.secret");
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String product = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        TestMerchants.completeSale(mockMvc, key, product, 4900, "u_1", "pay_1");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private Map<String, Object> delivery() {
        return jdbcTemplate.queryForMap(
                "SELECT status, attempt_count, last_status_code, next_attempt_at > now() + interval '50 seconds' AS later"
                        + " FROM webhook_deliveries");
    }

    @Test
    void should_deliver_signed_event_and_mark_succeeded() {
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        assertThat(received).hasSize(1);
        Received request = received.get(0);
        assertThat(request.eventType()).isEqualTo("checkout.completed");
        assertThat((String) JsonPath.read(request.body(), "$.type")).isEqualTo("checkout.completed");
        assertThat((String) JsonPath.read(request.body(), "$.data.customer_ref"))
                .isEqualTo("u_1");
        long timestamp = Long.parseLong(
                request.signature().substring(2, request.signature().indexOf(',')));
        assertThat(request.signature())
                .isEqualTo(WebhookSigner.header(secret, request.body().getBytes(), timestamp));
        assertThat(delivery()).containsEntry("status", "SUCCEEDED").containsEntry("attempt_count", 1);
    }

    @Test
    void should_schedule_retry_when_endpoint_returns_500() {
        responseCode.set(500);

        dispatcher.dispatchDue();

        assertThat(delivery())
                .containsEntry("status", "PENDING")
                .containsEntry("last_status_code", 500)
                .containsEntry("later", true);
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    void should_fail_permanently_after_last_attempt() {
        responseCode.set(503);
        jdbcTemplate.update("UPDATE webhook_deliveries SET attempt_count = 7");

        dispatcher.dispatchDue();

        assertThat(delivery()).containsEntry("status", "FAILED").containsEntry("attempt_count", 8);
    }

    @Test
    void should_send_once_when_two_dispatchers_run_concurrently() throws Exception {
        responseDelayMillis = 500;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> run = dispatcher::dispatchDue;

        pool.invokeAll(List.of(run, run));
        pool.shutdown();

        assertThat(received).hasSize(1);
    }

    @Test
    void should_not_send_to_disabled_endpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/dashboard/webhook_endpoints/" + endpointId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"));

        dispatcher.dispatchDue();

        assertThat(received).isEmpty();
        assertThat(delivery()).containsEntry("status", "FAILED");
    }
}
