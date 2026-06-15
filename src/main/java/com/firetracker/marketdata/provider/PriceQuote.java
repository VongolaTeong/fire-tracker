package com.firetracker.marketdata.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A latest close price from a {@link PriceProvider}, in the instrument's own listing currency.
 * The currency itself isn't carried here — the ingestion layer pairs the close with the
 * instrument's known currency, since price feeds don't always report one.
 */
public record PriceQuote(LocalDate date, BigDecimal close) {
}
