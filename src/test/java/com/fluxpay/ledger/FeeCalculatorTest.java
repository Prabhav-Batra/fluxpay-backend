package com.fluxpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.ledger.service.FeeCalculator;
import org.junit.jupiter.api.Test;

class FeeCalculatorTest {

    @Test
    void should_compute_exact_fee() {
        assertThat(FeeCalculator.platformFee(4900, 500)).isEqualTo(245);
    }

    @Test
    void should_round_half_up() {
        assertThat(FeeCalculator.platformFee(1999, 250)).isEqualTo(50); // 49.975
        assertThat(FeeCalculator.platformFee(1000, 5)).isEqualTo(1); // 0.5
        assertThat(FeeCalculator.platformFee(1000, 4)).isEqualTo(0); // 0.4
    }

    @Test
    void should_return_zero_for_zero_bps_and_full_amount_for_10000_bps() {
        assertThat(FeeCalculator.platformFee(4900, 0)).isZero();
        assertThat(FeeCalculator.platformFee(4900, 10_000)).isEqualTo(4900);
    }

    @Test
    void should_not_overflow_for_largest_product_amount() {
        assertThat(FeeCalculator.platformFee(50_000_000, 10_000)).isEqualTo(50_000_000);
    }
}
