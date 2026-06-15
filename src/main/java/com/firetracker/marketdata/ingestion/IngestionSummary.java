package com.firetracker.marketdata.ingestion;

/**
 * Outcome of one ingestion run: how many price and FX rows were upserted. (Upserts, so this
 * counts rows written — inserted or refreshed — not necessarily new rows.)
 */
public record IngestionSummary(int pricesUpserted, int ratesUpserted) {
}
