package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void should_accept_password_of_10_to_72_bytes() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_password_is_shorter_than_10_chars() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short"))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).details().get(0).code())
                .isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    void should_reject_when_password_exceeds_72_utf8_bytes_even_if_under_72_chars() {
        String devanagari = "क".repeat(30); // 30 chars, 90 bytes

        assertThatThrownBy(() -> PasswordPolicy.validate(devanagari))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).details().get(0).code())
                .isEqualTo("PASSWORD_TOO_LONG");
    }

    @Test
    void should_reject_when_password_is_null() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null)).isInstanceOf(FluxpayException.class);
    }
}
