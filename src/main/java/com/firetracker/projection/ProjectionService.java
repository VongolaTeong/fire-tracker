package com.firetracker.projection;

import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.portfolio.MissingMarketDataException;
import com.firetracker.portfolio.PortfolioService;
import com.firetracker.projection.dto.ProjectionBand;
import com.firetracker.projection.dto.ProjectionResponse;
import com.firetracker.transaction.Transaction;
import com.firetracker.transaction.TransactionRepository;
import com.firetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monte Carlo FIRE projection: continue the investor's DCA schedule forward from today's
 * portfolio and report the distribution of outcomes (a p10/p50/p90 fan) out to a target date.
 *
 * <p>Two inputs are derived from the actual ledger rather than assumed:
 * <ul>
 *   <li><b>Starting capital</b> — the current SGD market value (reusing {@link PortfolioService},
 *       so price + latest FX are applied consistently with the value endpoint).</li>
 *   <li><b>Monthly contribution</b> — inferred from historical BUYs: total capital deployed (each
 *       converted to SGD at its <em>transaction-date</em> rate, matching the cost-basis FX rule)
 *       divided by the number of months the buying spans. With no buy history it falls back to
 *       {@link ProjectionProperties#getMonthlyContribution()}.</li>
 * </ul>
 *
 * The return/volatility assumptions, path count and RNG seed come from configuration; the
 * stochastic mechanics live in {@link MonteCarloSimulation}.
 */
@Service
public class ProjectionService {

    private static final String REPORTING_CURRENCY = "SGD";
    private static final int MONEY_SCALE = 2;
    private static final int MONTHS_PER_YEAR = 12;

    private final TransactionRepository transactions;
    private final FxRateRepository fxRates;
    private final PortfolioService portfolio;
    private final ProjectionProperties props;
    private final Clock clock;

    public ProjectionService(TransactionRepository transactions,
                             FxRateRepository fxRates,
                             PortfolioService portfolio,
                             ProjectionProperties props,
                             Clock clock) {
        this.transactions = transactions;
        this.fxRates = fxRates;
        this.portfolio = portfolio;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjectionResponse project(LocalDate targetDate) {
        LocalDate asOf = LocalDate.now(clock);
        int months = (int) ChronoUnit.MONTHS.between(asOf, targetDate);
        if (months < 1) {
            throw new InvalidProjectionRequestException(
                    "targetDate must be at least one month after " + asOf + ", was " + targetDate);
        }

        BigDecimal initialValue = money(portfolio.value().totalValueSgd());
        BigDecimal monthlyContribution = inferMonthlyContribution(
                transactions.findAllByOrderByTransactionDateAscIdAsc());

        int[] checkpoints = yearlyCheckpoints(months);
        List<SimulatedBand> simulated = MonteCarloSimulation.simulate(new SimulationParams(
                initialValue.doubleValue(),
                monthlyContribution.doubleValue(),
                props.getAnnualMeanReturn(),
                props.getAnnualVolatility(),
                props.getPaths(),
                props.getSeed(),
                checkpoints));

        List<ProjectionBand> bands = simulated.stream()
                .map(b -> new ProjectionBand(
                        asOf.plusMonths(b.monthsFromStart()),
                        b.monthsFromStart(),
                        money(BigDecimal.valueOf(b.p10())),
                        money(BigDecimal.valueOf(b.p50())),
                        money(BigDecimal.valueOf(b.p90()))))
                .toList();

        BigDecimal totalContributions = money(monthlyContribution.multiply(BigDecimal.valueOf(months)));

        return new ProjectionResponse(
                REPORTING_CURRENCY, asOf, targetDate, months,
                initialValue, monthlyContribution, totalContributions,
                props.getAnnualMeanReturn(), props.getAnnualVolatility(), props.getPaths(),
                bands);
    }

    /**
     * Average monthly SGD deployed via BUYs over the period the buying spans (inclusive). Each
     * buy is converted at its transaction-date FX rate; SELLs and DIVIDENDs don't count as
     * contributions. Falls back to the configured default when there are no BUYs to learn from.
     */
    private BigDecimal inferMonthlyContribution(List<Transaction> ledger) {
        BigDecimal totalBuyCost = BigDecimal.ZERO;
        LocalDate firstBuy = null;
        LocalDate lastBuy = null;
        for (Transaction t : ledger) {
            if (t.getType() != TransactionType.BUY) {
                continue;
            }
            BigDecimal cost = t.getQuantity().multiply(t.getPricePerUnit()).add(t.getFee());
            totalBuyCost = totalBuyCost.add(toSgdAsOf(cost, t.getCurrency(), t.getTransactionDate()));
            if (firstBuy == null) {
                firstBuy = t.getTransactionDate(); // ledger is sorted ascending by date
            }
            lastBuy = t.getTransactionDate();
        }
        if (firstBuy == null) {
            return money(props.getMonthlyContribution());
        }
        long monthsSpanned = ChronoUnit.MONTHS.between(firstBuy, lastBuy) + 1; // inclusive, ≥ 1
        return totalBuyCost.divide(BigDecimal.valueOf(monthsSpanned), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Convert a native-currency amount to SGD at the rate in force on {@code date}. */
    private BigDecimal toSgdAsOf(BigDecimal amount, String currency, LocalDate date) {
        if (REPORTING_CURRENCY.equals(currency)) {
            return amount;
        }
        BigDecimal rate = fxRates
                .findFirstByBaseCurrencyAndQuoteCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                        currency, REPORTING_CURRENCY, date)
                .map(FxRate::getRate)
                .orElseThrow(() -> new MissingMarketDataException("No FX rate available for "
                        + currency + "->" + REPORTING_CURRENCY + " on or before " + date));
        return amount.multiply(rate);
    }

    /** Checkpoints for the fan: now (0), each anniversary, and the target month, ascending. */
    private static int[] yearlyCheckpoints(int months) {
        TreeSet<Integer> checkpoints = new TreeSet<>();
        checkpoints.add(0);
        for (int m = MONTHS_PER_YEAR; m < months; m += MONTHS_PER_YEAR) {
            checkpoints.add(m);
        }
        checkpoints.add(months);
        return checkpoints.stream().mapToInt(Integer::intValue).toArray();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
