package com.fluxpay.merchants.service;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugGenerator {

    static final int MAX_LENGTH = 40;
    static final String FALLBACK = "merchant";

    private SlugGenerator() {}

    public static String baseSlug(String businessName) {
        String ascii = Normalizer.normalize(businessName, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "");
        String slug =
                ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }
}
