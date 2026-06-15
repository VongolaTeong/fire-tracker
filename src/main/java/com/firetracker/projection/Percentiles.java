package com.firetracker.projection;

/**
 * Percentile of a sample, using linear interpolation between closest ranks — the same method
 * spreadsheets expose as {@code PERCENTILE.INC} (a.k.a. the type-7 quantile). Choosing the
 * spreadsheet method on purpose: like the XIRR engine, the Monte Carlo bands are meant to be
 * cross-checkable against a spreadsheet.
 *
 * <p>For a sorted sample of {@code n} values and a fraction {@code q ∈ [0, 1]}, the rank is
 * {@code h = (n − 1)·q}; the result interpolates between the values straddling {@code h}. With
 * a single value (or a degenerate, zero-spread sample) every percentile is that value.
 */
final class Percentiles {

    private Percentiles() {
    }

    /**
     * @param sortedAscending sample sorted ascending (not modified); must be non-empty
     * @param q               fraction in {@code [0, 1]} (e.g. 0.1 for p10)
     */
    static double of(double[] sortedAscending, double q) {
        if (sortedAscending.length == 0) {
            throw new IllegalArgumentException("percentile of an empty sample is undefined");
        }
        if (q < 0.0 || q > 1.0) {
            throw new IllegalArgumentException("percentile fraction must be in [0, 1], got " + q);
        }
        int n = sortedAscending.length;
        if (n == 1) {
            return sortedAscending[0];
        }
        double h = (n - 1) * q;
        int lo = (int) Math.floor(h);
        if (lo >= n - 1) {
            return sortedAscending[n - 1];
        }
        return sortedAscending[lo] + (h - lo) * (sortedAscending[lo + 1] - sortedAscending[lo]);
    }
}
