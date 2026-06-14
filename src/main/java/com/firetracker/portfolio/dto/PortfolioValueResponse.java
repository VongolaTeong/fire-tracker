package com.firetracker.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Current portfolio value: the total in the SGD reporting currency, the per-position
 * valuations behind it, and a per-currency breakdown showing the underlying FX exposure.
 */
public record PortfolioValueResponse(
        String reportingCurrency,
        BigDecimal totalValueSgd,
        List<PositionValue> positions,
        List<CurrencyValue> byCurrency
) {
}
