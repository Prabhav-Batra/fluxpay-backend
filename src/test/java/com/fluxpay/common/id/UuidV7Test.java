package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void should_set_version_7_and_rfc_variant_when_generated() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void should_embed_epoch_millis_in_top_48_bits_when_generated_with_fixed_time() {
        long millis = 1_759_363_200_000L;

        UUID id = UuidV7.generate(millis, new Random(1));

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void should_sort_by_creation_time_when_generated_at_increasing_millis() {
        UUID earlier = UuidV7.generate(1_000L, new Random(1));
        UUID later = UuidV7.generate(2_000L, new Random(1));

        assertThat(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()))
                .isNegative();
    }
}
