package com.firetracker.projection.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point of the projection fan: the p10/p50/p90 projected portfolio value (SGD) at
 * {@code date}, which is {@code monthsFromNow} months after the valuation date. The first band
 * is "now" (a collapsed point at the current value); the last is the target date.
 *
 * @param date         calendar date of this checkpoint
 * @param monthsFromNow months from the valuation date to this checkpoint
 * @param p10          pessimistic outcome — 10% of simulated paths end below this
 * @param p50          median outcome
 * @param p90          optimistic outcome — 10% of simulated paths end above this
 */
public record ProjectionBand(
        LocalDate date,
        int monthsFromNow,
        BigDecimal p10,
        BigDecimal p50,
        BigDecimal p90
) {
}
