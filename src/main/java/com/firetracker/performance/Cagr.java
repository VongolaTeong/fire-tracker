package com.firetracker.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Compound annual growth rate: the constant annual rate that takes {@code begin} to
 * {@code end} over {@code years}, i.e. {@code (end/begin)^(1/years) − 1}.
 *
 * <p>Unlike {@link Xirr}, CAGR ignores the timing of intermediate contributions — it's the
 * simple annualised growth of starting capital to ending value. Reported alongside XIRR for
 * context. Returns {@code null} when it isn't defined (non-positive starting capital, a
 * non-positive horizon, or a non-positive ending value).
 */
public final class Cagr {

    private static final int SCALE = 6;

    private Cagr() {
    }

    public static BigDecimal compute(BigDecimal begin, BigDecimal end, double years) {
        if (begin == null || end == null || begin.signum() <= 0 || years <= 0) {
            return null;
        }
        double ratio = end.doubleValue() / begin.doubleValue();
        if (ratio <= 0) {
            return null; // a real root of a non-positive ending value isn't meaningful
        }
        double cagr = Math.pow(ratio, 1.0 / years) - 1.0;
        return BigDecimal.valueOf(cagr).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
