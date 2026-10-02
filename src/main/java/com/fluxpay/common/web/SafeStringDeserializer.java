package com.fluxpay.common.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import java.io.IOException;

/**
 * Rejects JSON strings containing control characters other than tab, LF and CR. PostgreSQL cannot
 * store NUL in text columns, so such input must fail as a 400 at the boundary rather than a 500 later.
 */
public class SafeStringDeserializer extends StringDeserializer {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = super.deserialize(parser, context);
        if (value != null && containsForbiddenControlCharacter(value)) {
            return (String) context.handleWeirdStringValue(String.class, value, "contains control characters");
        }
        return value;
    }

    static boolean containsForbiddenControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
        }
        return false;
    }
}
