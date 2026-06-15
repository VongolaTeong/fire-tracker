package com.firetracker.projection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Monte Carlo projection of a DCA portfolio under Geometric Brownian Motion (GBM).
 *
 * <p>Each of {@code N} paths is stepped one month at a time. At the start of every future
 * month the DCA contribution is added, then the balance is grown by a random monthly factor
 *
 * <pre>  Vₜ = (Vₜ₋₁ + C) · exp( drift + vol · Z ),   Z ~ N(0, 1) </pre>
 *
 * where the monthly log-return parameters derive from the annual assumptions:
 *
 * <pre>  drift = (μ − σ²/2) / 12 ,   vol = σ / √12 . </pre>
 *
 * Over {@code m} months the accumulated log-return is therefore Normal with mean
 * {@code (μ − σ²/2)·(m/12)} and variance {@code σ²·(m/12)}, so (for a lump sum with no
 * contributions) the terminal value is lognormal with exactly the closed-form GBM quantiles —
 * which is how the engine is unit-tested.
 *
 * <p><b>Determinism:</b> a single {@link Random} seeded with {@link SimulationParams#seed()}
 * drives every draw, in a fixed sequential order, so the same params always yield identical
 * bands. {@code java.util.Random} is used deliberately: its bit-stream and {@code nextGaussian}
 * algorithm are specified and stable across JVMs, making seeded tests reproducible on any CI.
 *
 * <p><b>Numeric boundary:</b> like {@link com.firetracker.performance.Xirr}, the math here is
 * inherently floating-point, so the engine works in {@code double}; the caller rounds the
 * resulting bands to SGD money.
 */
final class MonteCarloSimulation {

    private static final int MONTHS_PER_YEAR = 12;
    private static final double P10 = 0.10;
    private static final double P50 = 0.50;
    private static final double P90 = 0.90;

    private MonteCarloSimulation() {
    }

    /**
     * Run the simulation and report percentile bands at each requested checkpoint.
     *
     * @return one {@link SimulatedBand} per checkpoint month, in the same (ascending) order as
     *         {@link SimulationParams#checkpointMonths()}
     */
    static List<SimulatedBand> simulate(SimulationParams params) {
        int[] checkpoints = params.checkpointMonths();
        validate(params, checkpoints);

        double drift = (params.annualMeanReturn() - 0.5 * params.annualVolatility() * params.annualVolatility())
                / MONTHS_PER_YEAR;
        double vol = params.annualVolatility() / Math.sqrt(MONTHS_PER_YEAR);
        int horizon = checkpoints[checkpoints.length - 1];

        // valuesAt[c][p] = value of path p when it reaches checkpoint month checkpoints[c].
        double[][] valuesAt = new double[checkpoints.length][params.paths()];
        Random rng = new Random(params.seed());

        for (int p = 0; p < params.paths(); p++) {
            double value = params.initialValue();
            int next = 0; // index into checkpoints of the next month to record
            next = recordCheckpointsAt(0, value, checkpoints, valuesAt, p, next);
            for (int month = 1; month <= horizon; month++) {
                value = (value + params.monthlyContribution()) * Math.exp(drift + vol * rng.nextGaussian());
                next = recordCheckpointsAt(month, value, checkpoints, valuesAt, p, next);
            }
        }

        List<SimulatedBand> bands = new ArrayList<>(checkpoints.length);
        for (int c = 0; c < checkpoints.length; c++) {
            double[] outcomes = valuesAt[c];
            Arrays.sort(outcomes);
            bands.add(new SimulatedBand(
                    checkpoints[c],
                    Percentiles.of(outcomes, P10),
                    Percentiles.of(outcomes, P50),
                    Percentiles.of(outcomes, P90)));
        }
        return bands;
    }

    /** Record {@code value} for path {@code p} into every checkpoint that lands on {@code month}. */
    private static int recordCheckpointsAt(int month, double value, int[] checkpoints,
                                           double[][] valuesAt, int p, int next) {
        while (next < checkpoints.length && checkpoints[next] == month) {
            valuesAt[next][p] = value;
            next++;
        }
        return next;
    }

    private static void validate(SimulationParams params, int[] checkpoints) {
        if (params.paths() < 1) {
            throw new IllegalArgumentException("simulation needs at least one path");
        }
        if (params.annualVolatility() < 0.0) {
            throw new IllegalArgumentException("annual volatility cannot be negative");
        }
        if (checkpoints == null || checkpoints.length == 0) {
            throw new IllegalArgumentException("at least one checkpoint month is required");
        }
        int prev = -1;
        for (int month : checkpoints) {
            if (month < 0) {
                throw new IllegalArgumentException("checkpoint months cannot be negative");
            }
            if (month <= prev) {
                throw new IllegalArgumentException("checkpoint months must be strictly ascending");
            }
            prev = month;
        }
    }
}
