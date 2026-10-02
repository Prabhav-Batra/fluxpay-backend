package com.fluxpay.common.id;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

public final class Base62 {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static final int UUID_LENGTH = 22;
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private static final BigInteger UUID_LIMIT = BigInteger.ONE.shiftLeft(128);

    private Base62() {}

    public static String encodeUuid(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        BigInteger value = new BigInteger(1, buffer.array());
        StringBuilder out = new StringBuilder();
        while (value.signum() > 0) {
            BigInteger[] divRem = value.divideAndRemainder(BASE);
            out.append(ALPHABET.charAt(divRem[1].intValue()));
            value = divRem[0];
        }
        while (out.length() < UUID_LENGTH) {
            out.append('0');
        }
        return out.reverse().toString();
    }

    public static Optional<UUID> decodeUuid(String encoded) {
        if (encoded == null || encoded.length() != UUID_LENGTH) {
            return Optional.empty();
        }
        BigInteger value = BigInteger.ZERO;
        for (char c : encoded.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                return Optional.empty();
            }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        if (value.compareTo(UUID_LIMIT) >= 0) {
            return Optional.empty();
        }
        byte[] raw = value.toByteArray();
        byte[] bytes = new byte[16];
        int copy = Math.min(raw.length, 16);
        System.arraycopy(raw, raw.length - copy, bytes, 16 - copy, copy);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    public static String random(int length, SecureRandom random) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }
}
