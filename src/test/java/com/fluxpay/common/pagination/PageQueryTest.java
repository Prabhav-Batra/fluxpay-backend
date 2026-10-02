package com.fluxpay.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.id.UuidV7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void should_default_to_20_and_max_uuid_when_no_params() {
        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, null, null);

        assertThat(query.limit()).isEqualTo(20);
        assertThat(query.before()).isEqualTo(new UUID(-1L, -1L));
        assertThat(query.fetchSize()).isEqualTo(21);
    }

    @Test
    void should_decode_cursor_when_starting_after_is_a_valid_id() {
        UUID id = UuidV7.generate();

        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, PublicId.of(IdPrefix.PRODUCT, id), 5);

        assertThat(query.before()).isEqualTo(id);
        assertThat(query.limit()).isEqualTo(5);
    }

    @Test
    void should_reject_limit_outside_1_to_100() {
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, null, 0))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_LIMIT");
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, null, 101))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_LIMIT");
    }

    @Test
    void should_reject_cursor_with_wrong_prefix_or_garbage() {
        String saleId = PublicId.of(IdPrefix.SALE, UuidV7.generate());

        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, saleId, null))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CURSOR");
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, "nope", null))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CURSOR");
    }
}
