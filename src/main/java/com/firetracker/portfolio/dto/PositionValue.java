package com.firetracker.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Current market value of one holding. Valued at the latest close price in the position's
 * own currency ({@code marketValueLocal}), then converted to the SGD reporting currency at
 * the latest FX rate ({@code marketValueSgd}). Both money figures are rounded to 2 dp.
 *
 * @param ticker           instrument identifier
 * @param units            net units held
 * @param currency         currency the price (and {@code marketValueLocal}) is expressed in
 * @param price            latest close price used for valuation
 * @param priceDate        date of that close price
 * @param marketValueLocal {@code units × price}, in {@code currency}
 * @param fxRate           {@code currency → SGD} rate applied (1 when already SGD)
 * @param marketValueSgd   {@code marketValueLocal × fxRate}, in SGD
 */
public record PositionValue(
        String ticker,
        BigDecimal units,
        String currency,
        BigDecimal price,
        LocalDate priceDate,
        BigDecimal marketValueLocal,
        BigDecimal fxRate,
        BigDecimal marketValueSgd
) {
}
