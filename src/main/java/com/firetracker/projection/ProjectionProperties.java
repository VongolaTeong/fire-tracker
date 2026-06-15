package com.firetracker.projection;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assumptions driving the Monte Carlo FIRE projection, overridable via {@code app.projection.*}
 * (environment variables in production). Defaults are deliberately conservative, broad-market
 * figures — not advice, just a starting point a caller can tune.
 *
 * <p>The {@code seed} is fixed so the projection is reproducible: the same request yields the
 * same fan, which keeps the demo stable and the tests deterministic. {@code monthlyContribution}
 * is only a <em>fallback</em>: the service normally infers the DCA rate from the actual ledger
 * and falls back to this when there is no buy history to infer from.
 */
@ConfigurationProperties(prefix = "app.projection")
public class ProjectionProperties {

    /** Assumed arithmetic mean annual return μ (fraction, e.g. 0.07 = 7%). */
    private double annualMeanReturn = 0.07;

    /** Assumed annual volatility σ (fraction, e.g. 0.15 = 15%). */
    private double annualVolatility = 0.15;

    /** Number of simulated paths; higher is smoother but slower. */
    private int paths = 10_000;

    /** RNG seed — fixed for reproducible projections. */
    private long seed = 42L;

    /** Fallback monthly DCA contribution (SGD) used only when the ledger has no buy history. */
    private BigDecimal monthlyContribution = new BigDecimal("1000.00");

    public double getAnnualMeanReturn() {
        return annualMeanReturn;
    }

    public void setAnnualMeanReturn(double annualMeanReturn) {
        this.annualMeanReturn = annualMeanReturn;
    }

    public double getAnnualVolatility() {
        return annualVolatility;
    }

    public void setAnnualVolatility(double annualVolatility) {
        this.annualVolatility = annualVolatility;
    }

    public int getPaths() {
        return paths;
    }

    public void setPaths(int paths) {
        this.paths = paths;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public BigDecimal getMonthlyContribution() {
        return monthlyContribution;
    }

    public void setMonthlyContribution(BigDecimal monthlyContribution) {
        this.monthlyContribution = monthlyContribution;
    }
}
