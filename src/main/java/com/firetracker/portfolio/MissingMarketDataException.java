package com.firetracker.portfolio;

/**
 * Raised when a held instrument can't be valued because the market data it needs is absent:
 * no price for the ticker, or no FX rate for its currency into the SGD reporting currency.
 * Surfaced as HTTP 422 rather than valued as zero, so a data gap is never mistaken for a
 * real figure.
 */
public class MissingMarketDataException extends RuntimeException {

    public MissingMarketDataException(String message) {
        super(message);
    }
}
