package com.fluxpay.common.id;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/** RFC 9562 version 7 UUIDs: 48-bit Unix millis followed by 74 random bits. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID generate() {
        return generate(System.currentTimeMillis(), RANDOM);
    }

    static UUID generate(long epochMillis, Random random) {
        byte[] r = new byte[10];
        random.nextBytes(r);
        long msb = ((epochMillis & 0xFFFF_FFFF_FFFFL) << 16) | 0x7000L | ((r[0] & 0x0FL) << 8) | (r[1] & 0xFFL);
        long lsb = 0x8000_0000_0000_0000L | ((r[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            lsb |= (r[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
