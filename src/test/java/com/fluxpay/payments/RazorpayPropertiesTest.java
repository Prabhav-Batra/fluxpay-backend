package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.RazorpayProperties;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RazorpayPropertiesTest {

    private static final Credentials TEST = new Credentials("rzp_test_k", "secret", "whsec");

    private static RazorpayProperties withLive(Credentials live) {
        return new RazorpayProperties("https://x", Duration.ofSeconds(1), Duration.ofSeconds(1), TEST, live);
    }

    @Test
    void should_treat_missing_or_blank_live_credentials_as_disabled() {
        assertThat(withLive(null).credentials(Mode.LIVE)).isEmpty();
        assertThat(withLive(new Credentials("", "", "")).credentials(Mode.LIVE)).isEmpty();
        assertThat(withLive(null).credentials(Mode.TEST)).contains(TEST);
    }

    @Test
    void should_fail_fast_when_live_credentials_are_partial() {
        assertThatThrownBy(() -> withLive(new Credentials("rzp_live_k", "", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
