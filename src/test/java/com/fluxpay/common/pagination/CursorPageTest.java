package com.fluxpay.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.common.id.IdPrefix;
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPageTest {

    private final PageQuery limitTwo = PageQuery.of(IdPrefix.PRODUCT, null, 2);

    @Test
    void should_trim_extra_row_and_set_cursor_when_more_rows_exist() {
        CursorPage<String> page = CursorPage.from(List.of(1, 2, 3), limitTwo, i -> "item" + i, s -> s);

        assertThat(page.data()).containsExactly("item1", "item2");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.cursor()).isEqualTo("item2");
    }

    @Test
    void should_have_no_cursor_when_last_page() {
        CursorPage<String> page = CursorPage.from(List.of(1), limitTwo, i -> "item" + i, s -> s);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.cursor()).isNull();
    }

    @Test
    void should_keep_cursor_and_flag_when_mapping() {
        CursorPage<String> page = CursorPage.from(List.of(1, 2, 3), limitTwo, i -> "item" + i, s -> s);

        CursorPage<Integer> mapped = page.map(String::length);

        assertThat(mapped.data()).containsExactly(5, 5);
        assertThat(mapped.cursor()).isEqualTo("item2");
        assertThat(mapped.hasMore()).isTrue();
    }
}
