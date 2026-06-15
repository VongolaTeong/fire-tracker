package com.firetracker.marketdata.provider;

import java.util.Optional;

/**
 * Source of latest FX rates, abstracted so the rate feed is swappable without touching the
 * ingestion logic.
 */
public interface FxProvider {

    /**
     * The latest available rate for {@code base → quote} (e.g. USD → SGD), or empty when the
     * feed has no data. Implementations must not throw on transport/feed errors — return empty
     * so a flaky feed never aborts the ingestion run.
     */
    Optional<FxQuote> fetchLatestRate(String base, String quote);
}
