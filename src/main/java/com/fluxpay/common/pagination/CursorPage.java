package com.fluxpay.common.pagination;

import java.util.List;
import java.util.function.Function;

public record CursorPage<T>(List<T> data, String cursor, boolean hasMore) {

    /** {@code rows} must have been fetched with {@link PageQuery#fetchSize()} so one extra row signals more. */
    public static <E, T> CursorPage<T> from(
            List<E> rows, PageQuery query, Function<E, T> mapper, Function<T, String> cursorOf) {
        boolean hasMore = rows.size() > query.limit();
        List<T> data = rows.stream().limit(query.limit()).map(mapper).toList();
        String cursor = hasMore ? cursorOf.apply(data.get(data.size() - 1)) : null;
        return new CursorPage<>(data, cursor, hasMore);
    }

    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(data.stream().map(mapper).toList(), cursor, hasMore);
    }
}
