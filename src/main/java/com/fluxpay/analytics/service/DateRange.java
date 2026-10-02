package com.fluxpay.analytics.service;

import com.fluxpay.common.error.FluxpayException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Inclusive local-date range, converted to [start, end) instants in the analytics zone. */
public record DateRange(LocalDate from, LocalDate to, ZoneId zone) {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    public static DateRange of(LocalDate from, LocalDate to, LocalDate today, ZoneId zone) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw FluxpayException.badRequest(
                    "INVALID_RANGE", "from must not be after to, and the range must be at most " + MAX_DAYS + " days");
        }
        return new DateRange(start, end, zone);
    }

    public Instant startInstant() {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant endInstant() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
