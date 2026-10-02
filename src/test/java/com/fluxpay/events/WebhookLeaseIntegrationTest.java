package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.service.WebhookDispatcher;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** A batch that outlasts the lease must not be re-sent or have its results overwritten by a second dispatcher. */
@TestPropertySource(properties = "fluxpay.webhooks.lease=2s")
class WebhookLeaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer server;

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void should_send_each_delivery_once_when_a_second_dispatcher_starts_after_lease_expiry() throws Exception {
        AtomicInteger received = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hooks", exchange -> {
            exchange.getRequestBody().readAllBytes();
            received.incrementAndGet();
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        mockMvc.perform(post("/api/v1/dashboard/webhook_endpoints")
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"http://localhost:%d/hooks\"}"
                        .formatted(server.getAddress().getPort())));
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String product = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        TestMerchants.completeSale(mockMvc, key, product, 4900, "u_1", "pay_1");
        String eventId = JsonPath.read(
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/dashboard/events")
                                .cookie(owner.session()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data[0].id");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/dashboard/events/" + eventId + "/resend")
                    .with(csrf())
                    .cookie(owner.session()));
        }

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(dispatcher::dispatchDue);
        TimeUnit.MILLISECONDS.sleep(2500);
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(dispatcher::dispatchDue);
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);

        assertThat(received).hasValue(5);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status || ':' || attempt_count FROM webhook_deliveries", String.class))
                .hasSize(5)
                .containsOnly("SUCCEEDED:1");
    }
}
