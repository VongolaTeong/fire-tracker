package com.firetracker.performance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Portfolio performance in the SGD reporting currency, as of {@code asOf}.
 *
 * @param reportingCurrency always SGD
 * @param asOf              valuation date the figures are computed against (today)
 * @param totalInvested     net capital deployed: Σ buy cost − Σ sell proceeds, each at its
 *                          transaction-date FX rate
 * @param currentValue      current market value (latest price + latest FX)
 * @param unrealizedPnl     {@code currentValue − totalInvested}
 * @param xirr              money-weighted annual return (fraction, e.g. 0.1234 = 12.34%);
 *                          {@code null} when the ledger can't produce a meaningful rate
 * @param cagr              simple annualised growth of invested → current value (fraction);
 *                          {@code null} when undefined
 */
public record PerformanceResponse(
        String reportingCurrency,
        LocalDate asOf,
        BigDecimal totalInvested,
        BigDecimal currentValue,
        BigDecimal unrealizedPnl,
        BigDecimal xirr,
        BigDecimal cagr
) {
}
