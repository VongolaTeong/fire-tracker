package com.firetracker.transaction;

import java.math.BigDecimal;

/**
 * Per-instrument net unit count, as aggregated by
 * {@link TransactionRepository#aggregateHoldings()}. A Spring Data projection: the query
 * aliases its columns to {@code ticker} and {@code units} to populate these accessors.
 */
public interface HoldingRow {

    String getTicker();

    BigDecimal getUnits();
}
