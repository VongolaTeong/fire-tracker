package com.firetracker.portfolio.dto;

import java.math.BigDecimal;

/**
 * Net units currently held in one instrument, with the instrument's trading currency for
 * context. Computed from the ledger (BUY − SELL; DIVIDEND ignored).
 */
public record HoldingResponse(
        String ticker,
        BigDecimal units,
        String currency
) {
}
