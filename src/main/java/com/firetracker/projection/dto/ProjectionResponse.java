package com.firetracker.projection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Monte Carlo FIRE projection in the SGD reporting currency. Echoes the assumptions the
 * simulation ran with (so the caller can see exactly what produced the numbers) alongside the
 * projected fan: percentile bands from "now" out to the target date.
 *
 * @param reportingCurrency   always SGD
 * @param asOf                valuation date the projection starts from (today)
 * @param targetDate          the requested FIRE horizon
 * @param months              whole months from {@code asOf} to {@code targetDate}
 * @param initialValue        current portfolio value used as starting capital (SGD)
 * @param monthlyContribution DCA amount continued each month — inferred from the ledger, or the
 *                            configured fallback when there is no buy history (SGD)
 * @param totalContributions  capital added over the horizon: {@code monthlyContribution × months}
 * @param annualMeanReturn    assumed mean annual return μ used by the simulation
 * @param annualVolatility    assumed annual volatility σ used by the simulation
 * @param paths               number of simulated paths
 * @param bands               the fan, one band per checkpoint, ascending by date; the last band
 *                            is the outcome at {@code targetDate}
 */
public record ProjectionResponse(
        String reportingCurrency,
        LocalDate asOf,
        LocalDate targetDate,
        int months,
        BigDecimal initialValue,
        BigDecimal monthlyContribution,
        BigDecimal totalContributions,
        double annualMeanReturn,
        double annualVolatility,
        int paths,
        List<ProjectionBand> bands
) {
}
