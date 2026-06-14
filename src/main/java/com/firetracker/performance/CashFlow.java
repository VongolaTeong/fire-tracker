package com.firetracker.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One dated cash flow in the reporting currency, from the investor's perspective: negative
 * for money paid out (a BUY), positive for money received (a SELL, a DIVIDEND, or the
 * terminal liquidation value). The amount is {@link BigDecimal} so it is built with
 * money-correct arithmetic; the XIRR solver reads it as a {@code double} only once, at the
 * boundary into numeric root-finding.
 */
public record CashFlow(LocalDate date, BigDecimal amount) {
}
