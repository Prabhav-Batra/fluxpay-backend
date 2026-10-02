package com.fluxpay.apikeys.service;

import com.fluxpay.common.id.Base62;
import com.fluxpay.common.tenant.Mode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A plaintext API key. Only its SHA-256 hash and lookup id are ever stored. */
public final class ApiKeySecret {

    static final int RANDOM_LENGTH = 44;
    static final int LOOKUP_LENGTH = 12;
    private static final Pattern FORMAT = Pattern.compile("^sk_(test|live)_([0-9A-Za-z]{" + RANDOM_LENGTH + "})$");

    private final String value;
    private final Mode mode;
    private final String lookupId;

    private ApiKeySecret(String value, Mode mode, String lookupId) {
        this.value = value;
        this.mode = mode;
        this.lookupId = lookupId;
    }

    public static ApiKeySecret generate(Mode mode, SecureRandom random) {
        String body = Base62.random(RANDOM_LENGTH, random);
        return new ApiKeySecret(prefix(mode) + body, mode, body.substring(0, LOOKUP_LENGTH));
    }

    public static Optional<ApiKeySecret> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Mode mode = Mode.parse(matcher.group(1)).orElseThrow();
        return Optional.of(new ApiKeySecret(raw, mode, matcher.group(2).substring(0, LOOKUP_LENGTH)));
    }

    public String value() {
        return value;
    }

    public Mode mode() {
        return mode;
    }

    public String lookupId() {
        return lookupId;
    }

    public String displayPrefix() {
        return prefix(mode) + lookupId;
    }

    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256(value));
    }

    public boolean matches(String storedHashHex) {
        return MessageDigest.isEqual(
                sha256Hex().getBytes(StandardCharsets.US_ASCII), storedHashHex.getBytes(StandardCharsets.US_ASCII));
    }

    static String prefix(Mode mode) {
        return "sk_" + mode.value() + "_";
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
