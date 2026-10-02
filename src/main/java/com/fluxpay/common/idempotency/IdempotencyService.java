package com.fluxpay.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Makes POST requests safe to retry (spec §9). The key row is claimed before the action runs, so a concurrent
 * duplicate gets IDEMPOTENCY_REQUEST_IN_PROGRESS instead of creating a second resource. Call outside any
 * transaction: each statement here auto-commits so other requests see the claim immediately.
 */
@Service
public class IdempotencyService {

    static final int MAX_KEY_LENGTH = 255;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> T execute(TenantContext tenant, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null) {
            return action.get();
        }
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw FluxpayException.badRequest(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        String requestHash = sha256Hex(write(request));
        int claimed = jdbc.update(
                "INSERT INTO idempotency_keys (merchant_id, mode, idempotency_key, request_hash, created_at)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                tenant.merchantId(),
                tenant.mode().name(),
                key,
                requestHash,
                Timestamp.from(Instant.now(clock)));
        if (claimed == 0) {
            return replay(tenant, key, requestHash, responseType);
        }
        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            jdbc.update(
                    "DELETE FROM idempotency_keys WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                    tenant.merchantId(),
                    tenant.mode().name(),
                    key);
            throw e;
        }
        jdbc.update(
                "UPDATE idempotency_keys SET response_body = ?::jsonb"
                        + " WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                write(response),
                tenant.merchantId(),
                tenant.mode().name(),
                key);
        return response;
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", Timestamp.from(cutoff));
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private <T> T replay(TenantContext tenant, String key, String requestHash, Class<T> responseType) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT request_hash, response_body::text AS response_body FROM idempotency_keys"
                            + " WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                    tenant.merchantId(),
                    tenant.mode().name(),
                    key);
        } catch (EmptyResultDataAccessException e) {
            throw inProgress();
        }
        if (!requestHash.equals(row.get("request_hash"))) {
            throw FluxpayException.conflict(
                    "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request");
        }
        Object body = row.get("response_body");
        if (body == null) {
            throw inProgress();
        }
        try {
            return objectMapper.readValue(body.toString(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response cannot be read", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise for idempotency", e);
        }
    }

    private static FluxpayException inProgress() {
        return FluxpayException.conflict(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still being processed");
    }
}
