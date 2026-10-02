package com.fluxpay.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModeTest {

    @Test
    void should_parse_lowercase_values() {
        assertThat(Mode.parse("test")).contains(Mode.TEST);
        assertThat(Mode.parse("live")).contains(Mode.LIVE);
    }

    @Test
    void should_reject_unknown_padded_or_uppercase_values() {
        assertThat(Mode.parse("prod")).isEmpty();
        assertThat(Mode.parse("LIVE")).isEmpty();
        assertThat(Mode.parse("live ")).isEmpty();
        assertThat(Mode.parse(null)).isEmpty();
    }
}
