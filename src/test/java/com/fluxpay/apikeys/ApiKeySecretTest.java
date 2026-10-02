package com.fluxpay.apikeys;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.apikeys.service.ApiKeySecret;
import com.fluxpay.common.tenant.Mode;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class ApiKeySecretTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void should_format_key_with_mode_prefix_and_44_chars_when_generated() {
        ApiKeySecret secret = ApiKeySecret.generate(Mode.LIVE, random);

        assertThat(secret.value()).matches("sk_live_[0-9A-Za-z]{44}");
        assertThat(secret.lookupId()).hasSize(12);
        assertThat(secret.displayPrefix()).isEqualTo("sk_live_" + secret.lookupId());
    }

    @Test
    void should_round_trip_and_match_hash_when_parsed() {
        ApiKeySecret generated = ApiKeySecret.generate(Mode.TEST, random);

        ApiKeySecret parsed = ApiKeySecret.parse(generated.value()).orElseThrow();

        assertThat(parsed.mode()).isEqualTo(Mode.TEST);
        assertThat(parsed.lookupId()).isEqualTo(generated.lookupId());
        assertThat(parsed.matches(generated.sha256Hex())).isTrue();
    }

    @Test
    void should_not_match_when_hash_belongs_to_another_key() {
        ApiKeySecret one = ApiKeySecret.generate(Mode.TEST, random);
        ApiKeySecret two = ApiKeySecret.generate(Mode.TEST, random);

        assertThat(one.matches(two.sha256Hex())).isFalse();
    }

    @Test
    void should_return_empty_when_parsing_malformed_keys() {
        assertThat(ApiKeySecret.parse(null)).isEmpty();
        assertThat(ApiKeySecret.parse("")).isEmpty();
        assertThat(ApiKeySecret.parse("sk_prod_" + "a".repeat(44))).isEmpty();
        assertThat(ApiKeySecret.parse("sk_test_" + "a".repeat(43))).isEmpty();
        assertThat(ApiKeySecret.parse("sk_test_" + "!".repeat(44))).isEmpty();
        assertThat(ApiKeySecret.parse("pk_test_" + "a".repeat(44))).isEmpty();
    }
}
