package com.firetracker.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Compound annual growth rate: simple, closed-form, and separately testable from XIRR. */
class CagrTest {

    @Test
    void computesAnnualisedGrowthOverWholeYears() {
        // 1000 -> 1210 over 2 years => (1.21)^(1/2) - 1 = 0.10.
        assertThat(Cagr.compute(new BigDecimal("1000"), new BigDecimal("1210"), 2.0))
                .isEqualByComparingTo("0.10");
    }

    @Test
    void handlesAFractionalYearHorizon() {
        // 1000 -> 1100 over exactly 1 year => 0.10.
        assertThat(Cagr.compute(new BigDecimal("1000"), new BigDecimal("1100"), 1.0))
                .isEqualByComparingTo("0.10");
    }

    @Test
    void returnsNullWhenStartingCapitalIsNotPositive() {
        // No meaningful growth rate off a zero/negative base.
        assertThat(Cagr.compute(BigDecimal.ZERO, new BigDecimal("1100"), 1.0)).isNull();
        assertThat(Cagr.compute(new BigDecimal("-500"), new BigDecimal("1100"), 1.0)).isNull();
    }

    @Test
    void returnsNullWhenHorizonIsNotPositive() {
        assertThat(Cagr.compute(new BigDecimal("1000"), new BigDecimal("1100"), 0.0)).isNull();
    }
}
