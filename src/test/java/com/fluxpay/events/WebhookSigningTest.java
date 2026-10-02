package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.events.service.RetrySchedule;
import com.fluxpay.events.service.WebhookSigner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSigningTest {

    @Test
    void should_sign_timestamp_dot_body_with_hmac_sha256() throws Exception {
        byte[] body = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_x".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected =
                HexFormat.of().formatHex(mac.doFinal("1700000000.{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8)));

        assertThat(WebhookSigner.header("whsec_x", body, 1_700_000_000L)).isEqualTo("t=1700000000,v1=" + expected);
    }

    @Test
    void should_back_off_then_stop_after_eighth_attempt() {
        assertThat(RetrySchedule.delayAfterAttempt(1)).contains(Duration.ofMinutes(1));
        assertThat(RetrySchedule.delayAfterAttempt(2)).contains(Duration.ofMinutes(5));
        assertThat(RetrySchedule.delayAfterAttempt(7)).contains(Duration.ofHours(24));
        assertThat(RetrySchedule.delayAfterAttempt(8)).isEmpty();
    }
}
