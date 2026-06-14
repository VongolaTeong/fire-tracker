package com.firetracker.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Internal rate of return for a series of irregularly-dated cash flows — the money-weighted
 * return reported as XIRR. Solves for the rate {@code r} that zeroes the net present value
 *
 * <pre>  NPV(r) = Σ amountᵢ / (1 + r)^yearsᵢ ,  yearsᵢ = days(dateᵢ − date₀) / 365 </pre>
 *
 * using Newton-Raphson with a bisection fallback when Newton fails to converge or steps out
 * of the {@code r > −1} domain. Day-count is Actual/365 to match spreadsheet {@code XIRR}.
 *
 * <p><b>Numeric boundary:</b> cash-flow <i>amounts</i> are built with {@code BigDecimal}
 * money math, but root-finding is inherently floating-point (fractional powers), so the
 * solver runs in {@code double} and returns the rate to within {@link #STEP_TOLERANCE}.
 */
public final class Xirr {

    private static final int DAYS_PER_YEAR = 365;
    private static final double INITIAL_GUESS = 0.10;
    private static final int MAX_NEWTON_ITERATIONS = 100;
    private static final double STEP_TOLERANCE = 1e-10;

    // Bracket search scans (1 + r) on a log scale, giving fine resolution near the r → −1
    // boundary (where deep-loss roots live) as well as out at high rates.
    private static final double U_MIN = 1e-6;
    private static final double U_MAX = 1e7;
    private static final double U_GROWTH = 1.25;
    private static final int MAX_BISECTION_ITERATIONS = 200;
    private static final double BISECTION_TOLERANCE = 1e-12;

    private Xirr() {
    }

    /**
     * @throws IllegalArgumentException if there are fewer than two flows, or they don't
     *                                  include at least one inflow and one outflow (no IRR exists)
     */
    public static double compute(List<CashFlow> flows) {
        validate(flows);

        LocalDate origin = flows.stream()
                .map(CashFlow::date)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        int n = flows.size();
        double[] amounts = new double[n];
        double[] years = new double[n];
        for (int i = 0; i < n; i++) {
            CashFlow flow = flows.get(i);
            amounts[i] = flow.amount().doubleValue();
            years[i] = (double) ChronoUnit.DAYS.between(origin, flow.date()) / DAYS_PER_YEAR;
        }

        double newton = newtonRaphson(amounts, years);
        return Double.isNaN(newton) ? bisection(amounts, years) : newton;
    }

    /** Returns the converged rate, or {@code NaN} to signal "fall back to bisection". */
    private static double newtonRaphson(double[] amounts, double[] years) {
        double rate = INITIAL_GUESS;
        for (int i = 0; i < MAX_NEWTON_ITERATIONS; i++) {
            double slope = dnpv(rate, amounts, years);
            if (Math.abs(slope) < 1e-12) {
                return Double.NaN; // derivative too flat to trust the step
            }
            double next = rate - npv(rate, amounts, years) / slope;
            if (!Double.isFinite(next) || next <= -1.0) {
                return Double.NaN; // diverged or stepped out of the r > −1 domain
            }
            if (Math.abs(next - rate) < STEP_TOLERANCE) {
                return next;
            }
            rate = next;
        }
        return Double.NaN; // didn't settle within the iteration budget
    }

    /** Scan for a sign change of NPV, then bisect that bracket to tolerance. */
    private static double bisection(double[] amounts, double[] years) {
        double prevRate = U_MIN - 1.0;
        double prevNpv = npv(prevRate, amounts, years);
        for (double u = U_MIN * U_GROWTH; u < U_MAX; u *= U_GROWTH) {
            double rate = u - 1.0;
            double value = npv(rate, amounts, years);
            if (value == 0.0) {
                return rate;
            }
            if (Math.signum(value) != Math.signum(prevNpv)) {
                return bisectBetween(prevRate, rate, amounts, years);
            }
            prevRate = rate;
            prevNpv = value;
        }
        throw new IllegalArgumentException(
                "XIRR did not converge: no sign change in NPV over the searched rate range");
    }

    private static double bisectBetween(double low, double high, double[] amounts, double[] years) {
        double lowNpv = npv(low, amounts, years);
        for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
            double mid = (low + high) / 2.0;
            if ((high - low) / 2.0 < BISECTION_TOLERANCE) {
                return mid;
            }
            double midNpv = npv(mid, amounts, years);
            if (midNpv == 0.0) {
                return mid;
            }
            if (Math.signum(midNpv) == Math.signum(lowNpv)) {
                low = mid;
                lowNpv = midNpv;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    private static double npv(double rate, double[] amounts, double[] years) {
        double base = 1.0 + rate;
        double sum = 0.0;
        for (int i = 0; i < amounts.length; i++) {
            sum += amounts[i] / Math.pow(base, years[i]);
        }
        return sum;
    }

    /** d/dr of {@link #npv}: Σ amountᵢ · (−yearsᵢ) · (1+r)^(−yearsᵢ−1). */
    private static double dnpv(double rate, double[] amounts, double[] years) {
        double base = 1.0 + rate;
        double sum = 0.0;
        for (int i = 0; i < amounts.length; i++) {
            sum += amounts[i] * (-years[i]) * Math.pow(base, -years[i] - 1.0);
        }
        return sum;
    }

    private static void validate(List<CashFlow> flows) {
        if (flows == null || flows.size() < 2) {
            throw new IllegalArgumentException("XIRR requires at least two cash flows");
        }
        boolean hasInflow = false;
        boolean hasOutflow = false;
        for (CashFlow flow : flows) {
            int sign = flow.amount().signum();
            if (sign > 0) {
                hasInflow = true;
            } else if (sign < 0) {
                hasOutflow = true;
            }
        }
        if (!hasInflow || !hasOutflow) {
            throw new IllegalArgumentException(
                    "XIRR requires at least one positive and one negative cash flow");
        }
    }
}
