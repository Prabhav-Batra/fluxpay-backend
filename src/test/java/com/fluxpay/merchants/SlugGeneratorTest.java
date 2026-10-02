package com.fluxpay.merchants;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.merchants.service.SlugGenerator;
import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void should_lowercase_and_hyphenate_when_name_has_spaces_and_symbols() {
        assertThat(SlugGenerator.baseSlug("Jextter  Esports & Co.")).isEqualTo("jextter-esports-co");
    }

    @Test
    void should_strip_accents_when_name_has_diacritics() {
        assertThat(SlugGenerator.baseSlug("Café Ñandú")).isEqualTo("cafe-nandu");
    }

    @Test
    void should_fall_back_to_merchant_when_name_has_no_ascii_letters() {
        assertThat(SlugGenerator.baseSlug("जेक्सटर")).isEqualTo("merchant");
    }

    @Test
    void should_truncate_to_40_chars_without_trailing_hyphen() {
        String slug = SlugGenerator.baseSlug("a".repeat(39) + " bcd");

        assertThat(slug).hasSizeLessThanOrEqualTo(40).doesNotEndWith("-");
    }
}
