package com.fluxpay.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataTest {

    private static String detailCode(Throwable e) {
        return ((FluxpayException) e).details().get(0).code();
    }

    @Test
    void should_return_empty_map_when_null() {
        assertThat(Metadata.validated(null)).isEmpty();
    }

    @Test
    void should_accept_valid_metadata() {
        assertThat(Metadata.validated(Map.of("coins", "500", "pack.tier", "pro")))
                .containsEntry("coins", "500");
    }

    @Test
    void should_reject_more_than_20_keys() {
        Map<String, String> big = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            big.put("k" + i, "v");
        }

        assertThatThrownBy(() -> Metadata.validated(big))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_TOO_MANY_KEYS"));
    }

    @Test
    void should_reject_bad_keys_and_values() {
        assertThatThrownBy(() -> Metadata.validated(Map.of("has space", "v")))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_KEY"));
        assertThatThrownBy(() -> Metadata.validated(Map.of("k", "x".repeat(501))))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_VALUE"));
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("k", null);
        assertThatThrownBy(() -> Metadata.validated(nullValue))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_VALUE"));
    }
}
