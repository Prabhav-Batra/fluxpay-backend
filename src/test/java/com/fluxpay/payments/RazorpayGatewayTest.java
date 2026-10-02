package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayOrder;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.RazorpayGateway;
import com.fluxpay.payments.service.RazorpayProperties;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import com.fluxpay.payments.service.WebhookSignatures;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RazorpayGatewayTest {

    private static final String BASE = "https://razorpay.test";

    private MockRestServiceServer server;
    private RazorpayGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RazorpayProperties properties = new RazorpayProperties(
                BASE,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new Credentials("rzp_test_k", "secret", "whsec_test"),
                null);
        gateway = new RazorpayGateway(builder.build(), properties);
    }

    @Test
    void should_create_order_with_basic_auth_and_receipt() {
        String auth =
                "Basic " + Base64.getEncoder().encodeToString("rzp_test_k:secret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", auth))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("cs_abc"))
                .andExpect(jsonPath("$.notes.checkout_session_id").value("cs_abc"))
                .andRespond(withSuccess(
                        "{\"id\":\"order_1\",\"amount\":4900,\"currency\":\"INR\",\"status\":\"created\"}",
                        MediaType.APPLICATION_JSON));

        GatewayOrder order =
                gateway.createOrder(Mode.TEST, 4900, "INR", "cs_abc", Map.of("checkout_session_id", "cs_abc"));

        assertThat(order).isEqualTo(new GatewayOrder("order_1", 4900, "INR"));
        server.verify();
    }

    @Test
    void should_raise_gateway_unavailable_when_razorpay_fails() {
        server.expect(requestTo(BASE + "/v1/orders")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createOrder(Mode.TEST, 4900, "INR", "cs_abc", Map.of()))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("GATEWAY_UNAVAILABLE");
    }

    @Test
    void should_raise_not_configured_for_live_mode_without_credentials() {
        assertThat(gateway.isEnabled(Mode.LIVE)).isFalse();
        assertThatThrownBy(() -> gateway.createOrder(Mode.LIVE, 4900, "INR", "cs_abc", Map.of()))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("GATEWAY_NOT_CONFIGURED");
    }

    @Test
    void should_parse_order_payments() {
        server.expect(requestTo(BASE + "/v1/orders/order_1/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"entity\":\"collection\",\"count\":1,\"items\":[{\"id\":\"pay_1\",\"order_id\":\"order_1\","
                                + "\"amount\":4900,\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"upi\","
                                + "\"fee\":116}]}",
                        MediaType.APPLICATION_JSON));

        List<GatewayPayment> payments = gateway.fetchOrderPayments(Mode.TEST, "order_1");

        assertThat(payments)
                .containsExactly(new GatewayPayment("pay_1", "order_1", 4900, "INR", "captured", "upi", 116));
    }

    @Test
    void should_verify_webhook_signature_with_mode_secret() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String valid = WebhookSignatures.hmacSha256Hex("whsec_test", body);

        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, valid)).isTrue();
        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, valid.replace('a', 'b')))
                .isFalse();
        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, null)).isFalse();
        assertThat(gateway.verifyWebhookSignature(Mode.LIVE, body, valid)).isFalse();
    }
}
