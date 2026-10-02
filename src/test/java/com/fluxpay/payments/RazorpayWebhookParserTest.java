package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import com.fluxpay.payments.service.GatewayWebhookEvent;
import com.fluxpay.payments.service.RazorpayWebhookParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RazorpayWebhookParserTest {

    private final RazorpayWebhookParser parser = new RazorpayWebhookParser(new ObjectMapper());

    private GatewayWebhookEvent parse(String json) {
        return parser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_parse_payment_captured() {
        GatewayWebhookEvent event = parse("{\"entity\":\"event\",\"event\":\"payment.captured\",\"payload\":"
                + "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":4900,"
                + "\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"card\",\"fee\":116,\"tax\":18}}}}");

        assertThat(event)
                .isEqualTo(new GatewayWebhookEvent.PaymentCaptured(
                        new GatewayPayment("pay_1", "order_1", 4900, "INR", "captured", "card", 116)));
    }

    @Test
    void should_parse_refund_processed() {
        GatewayWebhookEvent event = parse("{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":"
                + "{\"id\":\"rfnd_1\",\"payment_id\":\"pay_1\",\"amount\":2000,\"currency\":\"INR\"}}}}");

        assertThat(event)
                .isEqualTo(new GatewayWebhookEvent.RefundProcessed(new GatewayRefund("rfnd_1", "pay_1", 2000, "INR")));
    }

    @Test
    void should_ignore_other_events() {
        assertThat(parse("{\"event\":\"order.paid\",\"payload\":{}}"))
                .isEqualTo(new GatewayWebhookEvent.Ignored("order.paid"));
    }

    @Test
    void should_reject_malformed_json_or_missing_entity() {
        assertThatThrownBy(() -> parse("not json"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("MALFORMED_WEBHOOK");
        assertThatThrownBy(() -> parse("{\"event\":\"payment.captured\",\"payload\":{}}"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("MALFORMED_WEBHOOK");
    }
}
