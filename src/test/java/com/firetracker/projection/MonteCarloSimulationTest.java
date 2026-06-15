package com.firetracker.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reference tests for the Monte Carlo engine, written test-first. The headline case pins the
 * stochastic model to the <em>closed-form GBM lognormal quantiles</em>: for a lump sum with no
 * contributions the terminal value is lognormal, so p10/p50/p90 must match
 *
 * <pre>  quantile(q) = S₀ · exp( (μ − σ²/2)·T + σ·√T · z_q ) </pre>
 *
 * with the textbook standard-normal quantiles z. That is the externally-verifiable anchor (the
 * Monte Carlo analogue of XIRR's Microsoft example); the remaining tests pin the deterministic
 * mechanics (zero-volatility future value, contribution handling) and reproducibility. Pure
 * unit tests: no Spring, no database.
 */
class MonteCarloSimulationTest {

    // Standard-normal quantiles: P(Z ≤ z) = 0.10 and 0.90 respectively.
    private static final double Z10 = -1.2815515594600999;
    private static final double Z90 = 1.2815515594600999;

    @Test
    void lumpSumPercentilesMatchClosedFormLognormalQuantiles() {
        double s0 = 10_000.0;
        double mu = 0.07;
        double sigma = 0.15;
        int months = 120; // T = 10 years
        double years = months / 12.0;

        SimulationParams params = new SimulationParams(
                s0, 0.0, mu, sigma, 200_000, 12_345L, new int[] {months});
        SimulatedBand terminal = MonteCarloSimulation.simulate(params).get(0);

        double logMean = (mu - 0.5 * sigma * sigma) * years;
        double logSd = sigma * Math.sqrt(years);
        double expectedP10 = s0 * Math.exp(logMean + logSd * Z10);
        double expectedP50 = s0 * Math.exp(logMean);
        double expectedP90 = s0 * Math.exp(logMean + logSd * Z90);

        // With 200k paths the sample quantiles sit within a couple of percent of the theory.
        assertThat(terminal.p10()).isCloseTo(expectedP10, withinPercentage(3));
        assertThat(terminal.p50()).isCloseTo(expectedP50, withinPercentage(3));
        assertThat(terminal.p90()).isCloseTo(expectedP90, withinPercentage(3));
    }

    @Test
    void zeroVolatilityCollapsesToTheClosedFormFutureValue() {
        double s0 = 10_000.0;
        double contribution = 500.0;
        double mu = 0.06;
        int months = 24;

        SimulationParams params = new SimulationParams(
                s0, contribution, mu, 0.0, 1_000, 1L, new int[] {months});
        SimulatedBand terminal = MonteCarloSimulation.simulate(params).get(0);

        // With σ = 0 every path is identical: Vₜ = (Vₜ₋₁ + C)·exp(μ/12), starting from S₀.
        double g = Math.exp(mu / 12.0);
        double expected = s0;
        for (int m = 0; m < months; m++) {
            expected = (expected + contribution) * g;
        }

        assertThat(terminal.p10()).isCloseTo(expected, within(1e-6));
        assertThat(terminal.p50()).isCloseTo(expected, within(1e-6));
        assertThat(terminal.p90()).isCloseTo(expected, within(1e-6));
    }

    @Test
    void checkpointZeroReportsTheKnownStartingValueWithNoSpread() {
        SimulationParams params = new SimulationParams(
                25_000.0, 1_000.0, 0.07, 0.15, 5_000, 7L, new int[] {0, 12, 24});
        List<SimulatedBand> bands = MonteCarloSimulation.simulate(params);

        SimulatedBand start = bands.get(0);
        assertThat(start.monthsFromStart()).isZero();
        assertThat(start.p10()).isEqualTo(25_000.0);
        assertThat(start.p50()).isEqualTo(25_000.0);
        assertThat(start.p90()).isEqualTo(25_000.0);
    }

    @Test
    void volatilityProducesAWideningOrderedFan() {
        SimulationParams params = new SimulationParams(
                10_000.0, 500.0, 0.07, 0.18, 20_000, 99L, new int[] {0, 60, 120});
        List<SimulatedBand> bands = MonteCarloSimulation.simulate(params);

        double prevSpread = -1.0;
        for (SimulatedBand band : bands) {
            assertThat(band.p10()).isLessThanOrEqualTo(band.p50());
            assertThat(band.p50()).isLessThanOrEqualTo(band.p90());
            double spread = band.p90() - band.p10();
            assertThat(spread).isGreaterThan(prevSpread); // uncertainty grows with the horizon
            prevSpread = spread;
        }
    }

    @Test
    void sameSeedProducesIdenticalResults() {
        SimulationParams params = new SimulationParams(
                10_000.0, 500.0, 0.07, 0.15, 10_000, 2_024L, new int[] {0, 60, 120, 180});

        List<SimulatedBand> first = MonteCarloSimulation.simulate(params);
        List<SimulatedBand> second = MonteCarloSimulation.simulate(params);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void higherAssumedReturnLiftsTheMedianOutcome() {
        int[] horizon = {120};
        SimulatedBand modest = MonteCarloSimulation.simulate(
                new SimulationParams(10_000.0, 500.0, 0.04, 0.15, 20_000, 5L, horizon)).get(0);
        SimulatedBand bullish = MonteCarloSimulation.simulate(
                new SimulationParams(10_000.0, 500.0, 0.10, 0.15, 20_000, 5L, horizon)).get(0);

        assertThat(bullish.p50()).isGreaterThan(modest.p50());
    }
}
