package com.firetracker.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Pins the percentile helper to spreadsheet {@code PERCENTILE.INC}, so the Monte Carlo bands
 * are cross-checkable against a spreadsheet (the same philosophy as the XIRR reference tests).
 */
class PercentilesTest {

    private static final double TOL = 1e-9;

    @Test
    void matchesMicrosoftDocumentedPercentileExample() {
        // Microsoft's PERCENTILE example: PERCENTILE.INC({1,2,3,4}, 0.3) = 1.9.
        double[] data = {1, 2, 3, 4};
        assertThat(Percentiles.of(data, 0.3)).isCloseTo(1.9, within(TOL));
    }

    @Test
    void interpolatesQuartilesBetweenRanks() {
        double[] data = {1, 2, 3, 4};
        assertThat(Percentiles.of(data, 0.25)).isCloseTo(1.75, within(TOL));
        assertThat(Percentiles.of(data, 0.50)).isCloseTo(2.50, within(TOL));
        assertThat(Percentiles.of(data, 0.75)).isCloseTo(3.25, within(TOL));
    }

    @Test
    void returnsMinAndMaxAtTheBoundaries() {
        double[] data = {5, 10, 15, 20, 25};
        assertThat(Percentiles.of(data, 0.0)).isEqualTo(5);
        assertThat(Percentiles.of(data, 1.0)).isEqualTo(25);
    }

    @Test
    void everyPercentileOfASingleValueIsThatValue() {
        double[] data = {42};
        assertThat(Percentiles.of(data, 0.1)).isEqualTo(42);
        assertThat(Percentiles.of(data, 0.9)).isEqualTo(42);
    }

    @Test
    void rejectsAnEmptySampleAndOutOfRangeFractions() {
        assertThatThrownBy(() -> Percentiles.of(new double[0], 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Percentiles.of(new double[] {1, 2}, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
