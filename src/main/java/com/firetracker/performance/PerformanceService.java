package com.firetracker.performance;

import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.performance.dto.PerformanceResponse;
import com.firetracker.portfolio.MissingMarketDataException;
import com.firetracker.portfolio.PortfolioService;
import com.firetracker.transaction.Transaction;
import com.firetracker.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portfolio performance analytics: money-weighted return (XIRR), CAGR, total invested, and
 * unrealized P/L, all in the SGD reporting currency.
 *
 * <p>FX is kept deliberately distinct: every cash flow is converted at its <em>transaction-date</em>
 * rate (cost basis), while the terminal current value uses the <em>latest</em> rate (reused
 * from {@link PortfolioService}). Cash-flow amounts are built with {@link BigDecimal}; the
 * numeric IRR solve happens inside {@link Xirr}.
 */
@Service
public class PerformanceService {

    private static final String REPORTING_CURRENCY = "SGD";
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 6;
    private static final double DAYS_PER_YEAR = 365.0;

    private final TransactionRepository transactions;
    private final FxRateRepository fxRates;
    private final PortfolioService portfolio;
    private final Clock clock;

    public PerformanceService(TransactionRepository transactions,
                              FxRateRepository fxRates,
                              PortfolioService portfolio,
                              Clock clock) {
        this.transactions = transactions;
        this.fxRates = fxRates;
        this.portfolio = portfolio;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PerformanceResponse performance() {
        LocalDate asOf = LocalDate.now(clock);
        List<Transaction> ledger = transactions.findAllByOrderByTransactionDateAscIdAsc();
        BigDecimal currentValue = money(portfolio.value().totalValueSgd());

        BigDecimal totalBuyCost = BigDecimal.ZERO;
        BigDecimal totalSellProceeds = BigDecimal.ZERO;
        List<CashFlow> flows = new ArrayList<>();

        for (Transaction t : ledger) {
            BigDecimal gross = t.getQuantity().multiply(t.getPricePerUnit());
            switch (t.getType()) {
                case BUY -> {
                    BigDecimal cost = toSgdAsOf(gross.add(t.getFee()), t.getCurrency(), t.getTransactionDate());
                    totalBuyCost = totalBuyCost.add(cost);
                    flows.add(new CashFlow(t.getTransactionDate(), cost.negate()));
                }
                case SELL -> {
                    BigDecimal proceeds = toSgdAsOf(gross.subtract(t.getFee()), t.getCurrency(), t.getTransactionDate());
                    totalSellProceeds = totalSellProceeds.add(proceeds);
                    flows.add(new CashFlow(t.getTransactionDate(), proceeds));
                }
                case DIVIDEND -> {
                    BigDecimal income = toSgdAsOf(gross.subtract(t.getFee()), t.getCurrency(), t.getTransactionDate());
                    flows.add(new CashFlow(t.getTransactionDate(), income));
                }
            }
        }

        BigDecimal totalInvested = money(totalBuyCost.subtract(totalSellProceeds));
        BigDecimal unrealizedPnl = money(currentValue.subtract(totalInvested));

        // The terminal flow treats the portfolio as liquidated at current value, today.
        flows.add(new CashFlow(asOf, currentValue));
        BigDecimal xirr = tryXirr(flows);
        BigDecimal cagr = Cagr.compute(totalInvested, currentValue, yearsSinceFirst(ledger, asOf));

        return new PerformanceResponse(
                REPORTING_CURRENCY, asOf, totalInvested, currentValue, unrealizedPnl, xirr, cagr);
    }

    /** Convert a native-currency amount to SGD at the rate in force on {@code date}. */
    private BigDecimal toSgdAsOf(BigDecimal amount, String currency, LocalDate date) {
        if (REPORTING_CURRENCY.equals(currency)) {
            return money(amount);
        }
        BigDecimal rate = fxRates
                .findFirstByBaseCurrencyAndQuoteCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                        currency, REPORTING_CURRENCY, date)
                .map(FxRate::getRate)
                .orElseThrow(() -> new MissingMarketDataException("No FX rate available for "
                        + currency + "->" + REPORTING_CURRENCY + " on or before " + date));
        return money(amount.multiply(rate));
    }

    /** XIRR, or {@code null} when the ledger is empty/degenerate (no inflow+outflow pair). */
    private static BigDecimal tryXirr(List<CashFlow> flows) {
        try {
            return BigDecimal.valueOf(Xirr.compute(flows)).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double yearsSinceFirst(List<Transaction> ledger, LocalDate asOf) {
        if (ledger.isEmpty()) {
            return 0.0;
        }
        LocalDate first = ledger.get(0).getTransactionDate(); // sorted ascending
        return ChronoUnit.DAYS.between(first, asOf) / DAYS_PER_YEAR;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
