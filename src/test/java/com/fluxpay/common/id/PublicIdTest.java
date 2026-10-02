package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIdTest {

    @Test
    void should_prefix_and_round_trip_when_formatting_an_id() {
        UUID id = UuidV7.generate();

        String publicId = PublicId.of(IdPrefix.PRODUCT, id);

        assertThat(publicId).startsWith("prod_").hasSize("prod_".length() + 22);
        assertThat(PublicId.parse(IdPrefix.PRODUCT, publicId)).contains(id);
    }

    @Test
    void should_return_empty_when_prefix_does_not_match() {
        String saleId = PublicId.of(IdPrefix.SALE, UuidV7.generate());

        assertThat(PublicId.parse(IdPrefix.PRODUCT, saleId)).isEmpty();
    }

    @Test
    void should_return_empty_when_input_is_null_or_garbage() {
        assertThat(PublicId.parse(IdPrefix.PRODUCT, null)).isEmpty();
        assertThat(PublicId.parse(IdPrefix.PRODUCT, "prod_")).isEmpty();
        assertThat(PublicId.parse(IdPrefix.PRODUCT, "prod_not-an-id")).isEmpty();
    }
}
