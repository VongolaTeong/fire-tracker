package com.firetracker.portfolio.dto;

import java.math.BigDecimal;

/**
 * Portfolio value contributed by all holdings priced in one currency: the summed local
 * amount and its SGD equivalent. The SGD figures across currencies add up to the portfolio
 * total, so this breakdown shows the FX exposure behind that total.
 */
public record CurrencyValue(
        String currency,
        BigDecimal marketValueLocal,
        BigDecimal marketValueSgd
) {
}
