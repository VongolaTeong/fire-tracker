package com.firetracker.marketdata.provider;

import java.util.Optional;

/**
 * Source of latest close prices, abstracted so the market-data feed is swappable (manual CSV
 * first, a free API in Step 5, something else later) without touching the ingestion logic.
 */
public interface PriceProvider {

    /**
     * The latest available close price for a provider-specific {@code symbol}, or empty when
     * the feed has no data for it. Implementations must not throw on transport/feed errors —
     * return empty so one bad symbol never aborts the ingestion run.
     */
    Optional<PriceQuote> fetchLatestPrice(String symbol);
}
