package com.firetracker.projection;

/**
 * Inputs to the {@link MonteCarloSimulation}, already reduced to {@code double} at the numeric
 * boundary (money is built with {@code BigDecimal} in the service; the simulation itself is
 * inherently floating-point — exponentials and Gaussian draws — so it runs in {@code double},
 * mirroring the {@link com.firetracker.performance.Xirr} solver's boundary).
 *
 * @param initialValue        starting portfolio value (SGD)
 * @param monthlyContribution DCA amount added at the start of each future month (SGD)
 * @param annualMeanReturn    assumed arithmetic mean annual return μ (e.g. 0.07)
 * @param annualVolatility    assumed annual volatility σ (e.g. 0.15); 0 ⇒ deterministic path
 * @param paths               number of simulated paths N
 * @param seed                RNG seed; a fixed seed makes the whole simulation reproducible
 * @param checkpointMonths    month offsets at which to report percentile bands, sorted
 *                            ascending and distinct; {@code 0} reports the (known) starting
 *                            value and the last entry is the projection horizon
 */
record SimulationParams(
        double initialValue,
        double monthlyContribution,
        double annualMeanReturn,
        double annualVolatility,
        int paths,
        long seed,
        int[] checkpointMonths
) {
}
