package com.firetracker.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point on the portfolio value-over-time series: the total SGD market value of all holdings
 * as-of {@code date}, valued with the price and FX rate that were current on that date. Drives
 * the dashboard's value-over-time chart.
 */
public record ValuePoint(LocalDate date, BigDecimal valueSgd) {
}
