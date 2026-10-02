package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void should_round_trip_when_encoding_and_decoding_a_uuid() {
        UUID id = UUID.randomUUID();

        assertThat(Base62.decodeUuid(Base62.encodeUuid(id))).contains(id);
    }

    @Test
    void should_produce_22_chars_when_encoding_the_zero_uuid() {
        assertThat(Base62.encodeUuid(new UUID(0, 0))).isEqualTo("0".repeat(22));
    }

    @Test
    void should_round_trip_when_encoding_the_max_uuid() {
        UUID max = new UUID(-1L, -1L);

        assertThat(Base62.decodeUuid(Base62.encodeUuid(max))).contains(max);
    }

    @Test
    void should_return_empty_when_decoding_wrong_length_or_bad_chars_or_overflow() {
        assertThat(Base62.decodeUuid("abc")).isEmpty();
        assertThat(Base62.decodeUuid("!".repeat(22))).isEmpty();
        assertThat(Base62.decodeUuid("z".repeat(22))).isEmpty();
        assertThat(Base62.decodeUuid(null)).isEmpty();
    }

    @Test
    void should_return_only_alphabet_chars_when_generating_random_string() {
        String value = Base62.random(40, new SecureRandom());

        assertThat(value).hasSize(40).matches("[0-9A-Za-z]+");
    }
}
