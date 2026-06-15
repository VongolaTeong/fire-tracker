package com.firetracker.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A latest FX rate from an {@link FxProvider}: {@code 1 base = rate quote} on {@code date},
 * matching the convention stored in {@code fx_rate}.
 */
public record FxQuote(LocalDate date, BigDecimal rate) {
}
