package com.fluxpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import org.junit.jupiter.api.Test;

class RedirectUrlPolicyTest {

    private static String code(Throwable e) {
        return ((FluxpayException) e).details().get(0).code();
    }

    @Test
    void should_accept_https_in_both_modes_and_null() {
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.LIVE, "success_url", "https://jextter.com/paid?x=1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", null))
                .doesNotThrowAnyException();
    }

    @Test
    void should_accept_http_localhost_only_in_test_mode() {
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "http://localhost:3000/ok"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.LIVE, "success_url", "http://localhost:3000/ok"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INSECURE_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "http://jextter.com/ok"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INSECURE_URL"));
    }

    @Test
    void should_reject_scripts_relative_userinfo_and_overlong_urls() {
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "javascript:alert(1)"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "/relative"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "https://user@evil.com"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(
                        () -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "https://a.com/" + "x".repeat(2048)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("URL_TOO_LONG"));
    }
}
