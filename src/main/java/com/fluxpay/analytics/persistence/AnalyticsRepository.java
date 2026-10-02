package com.fluxpay.analytics.persistence;

import com.fluxpay.common.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only aggregates over the sales and ledger tables (spec §10: SQL, no separate analytics store). */
@Repository
public class AnalyticsRepository {

    public record SalesTotals(long count, long amount) {}

    public record DayRow(LocalDate date, long count, long amount) {}

    public record ProductRow(UUID productId, long units, long revenue) {}

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SalesTotals salesTotals(TenantContext tenant, Instant start, Instant end) {
        return jdbc.queryForObject(
                "SELECT count(*) AS n, coalesce(sum(amount), 0) AS total FROM sales"
                        + " WHERE merchant_id = ? AND mode = ? AND created_at >= ? AND created_at < ?",
                (rs, i) -> new SalesTotals(rs.getLong("n"), rs.getLong("total")),
                args(tenant, start, end));
    }

    /** Signed ledger sums by entry type within the range. */
    public Map<String, Long> ledgerTotals(TenantContext tenant, Instant start, Instant end) {
        Map<String, Long> totals = new HashMap<>();
        jdbc.query(
                "SELECT type, sum(amount) AS total FROM ledger_entries"
                        + " WHERE merchant_id = ? AND mode = ? AND created_at >= ? AND created_at < ? GROUP BY type",
                rs -> {
                    totals.put(rs.getString("type"), rs.getLong("total"));
                },
                args(tenant, start, end));
        return totals;
    }

    public List<DayRow> salesByDay(TenantContext tenant, Instant start, Instant end, ZoneId zone) {
        return jdbc.query(
                "SELECT (created_at AT TIME ZONE ?)::date AS day, count(*) AS n, sum(amount) AS total FROM sales"
                        + " WHERE merchant_id = ? AND mode = ? AND created_at >= ? AND created_at < ?"
                        + " GROUP BY day ORDER BY day",
                (rs, i) -> new DayRow(rs.getObject("day", LocalDate.class), rs.getLong("n"), rs.getLong("total")),
                zone.getId(),
                tenant.merchantId(),
                tenant.mode().name(),
                Timestamp.from(start),
                Timestamp.from(end));
    }

    public List<ProductRow> topProducts(TenantContext tenant, Instant start, Instant end, int limit) {
        return jdbc.query(
                "SELECT product_id, count(*) AS units, sum(amount) AS revenue FROM sales"
                        + " WHERE merchant_id = ? AND mode = ? AND created_at >= ? AND created_at < ?"
                        + " GROUP BY product_id ORDER BY revenue DESC, units DESC LIMIT ?",
                (rs, i) -> new ProductRow(
                        rs.getObject("product_id", UUID.class), rs.getLong("units"), rs.getLong("revenue")),
                tenant.merchantId(),
                tenant.mode().name(),
                Timestamp.from(start),
                Timestamp.from(end),
                limit);
    }

    private static Object[] args(TenantContext tenant, Instant start, Instant end) {
        return new Object[] {tenant.merchantId(), tenant.mode().name(), Timestamp.from(start), Timestamp.from(end)};
    }
}
