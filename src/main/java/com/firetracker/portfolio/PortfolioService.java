package com.firetracker.portfolio;

import com.firetracker.instrument.Instrument;
import com.firetracker.instrument.InstrumentRepository;
import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.portfolio.dto.CurrencyValue;
import com.firetracker.portfolio.dto.HoldingResponse;
import com.firetracker.portfolio.dto.PortfolioValueResponse;
import com.firetracker.portfolio.dto.PositionValue;
import com.firetracker.transaction.HoldingRow;
import com.firetracker.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the transaction ledger into a current portfolio snapshot: net units per instrument
 * and their market value in the SGD reporting currency.
 *
 * <p>Valuation is FX-correct and money-correct: each holding is valued at its latest close
 * price in the instrument's own currency, then converted to SGD at the latest FX rate, using
 * {@link BigDecimal} throughout. (Transaction-date / cost-basis FX is a separate concern,
 * handled by the performance step.)
 */
@Service
public class PortfolioService {

    /** Everything is reported in SGD; a holding already priced in SGD converts at rate 1. */
    private static final String REPORTING_CURRENCY = "SGD";

    /** Money figures are rounded to cents; FX rates and unit counts keep full precision. */
    private static final int MONEY_SCALE = 2;

    private final TransactionRepository transactions;
    private final InstrumentRepository instruments;
    private final PriceHistoryRepository prices;
    private final FxRateRepository fxRates;

    public PortfolioService(TransactionRepository transactions,
                            InstrumentRepository instruments,
                            PriceHistoryRepository prices,
                            FxRateRepository fxRates) {
        this.transactions = transactions;
        this.instruments = instruments;
        this.prices = prices;
        this.fxRates = fxRates;
    }

    /** Net units held per instrument (zero-net positions omitted), with trading currency. */
    @Transactional(readOnly = true)
    public List<HoldingResponse> holdings() {
        List<HoldingRow> rows = nonZeroHoldings();
        Map<String, String> currencyByTicker = currencyByTicker(rows);
        return rows.stream()
                .map(h -> new HoldingResponse(
                        h.getTicker(), h.getUnits(), currencyByTicker.get(h.getTicker())))
                .toList();
    }

    /**
     * Current market value: each holding valued at its latest price and converted to SGD,
     * with a per-currency breakdown and the SGD total. Raises {@link MissingMarketDataException}
     * (HTTP 422) if a held instrument has no price, or its currency no FX rate into SGD.
     */
    @Transactional(readOnly = true)
    public PortfolioValueResponse value() {
        List<PositionValue> positions = nonZeroHoldings().stream()
                .map(this::valuePosition)
                .toList();

        BigDecimal totalValueSgd = positions.stream()
                .map(PositionValue::marketValueSgd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioValueResponse(
                REPORTING_CURRENCY, totalValueSgd, positions, breakdownByCurrency(positions));
    }

    private PositionValue valuePosition(HoldingRow holding) {
        String ticker = holding.getTicker();
        BigDecimal units = holding.getUnits();

        PriceHistory price = prices.findFirstByTickerOrderByPriceDateDesc(ticker)
                .orElseThrow(() -> new MissingMarketDataException("No price available for " + ticker));

        // Round once per position; totals then add already-rounded figures so the breakdown
        // reconciles to the cent. Multiply at full precision before rounding to avoid drift.
        BigDecimal rawLocal = units.multiply(price.getClosePrice());
        BigDecimal fxRate = fxRateToSgd(price.getCurrency());
        BigDecimal marketValueLocal = money(rawLocal);
        BigDecimal marketValueSgd = money(rawLocal.multiply(fxRate));

        return new PositionValue(
                ticker, units, price.getCurrency(), price.getClosePrice(), price.getPriceDate(),
                marketValueLocal, fxRate, marketValueSgd);
    }

    private BigDecimal fxRateToSgd(String currency) {
        if (REPORTING_CURRENCY.equals(currency)) {
            return BigDecimal.ONE;
        }
        return fxRates.findFirstByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(currency, REPORTING_CURRENCY)
                .map(FxRate::getRate)
                .orElseThrow(() -> new MissingMarketDataException(
                        "No FX rate available for " + currency + "->" + REPORTING_CURRENCY));
    }

    private static List<CurrencyValue> breakdownByCurrency(List<PositionValue> positions) {
        // TreeMaps keep the breakdown ordered by currency code for stable output.
        Map<String, BigDecimal> localByCurrency = new TreeMap<>();
        Map<String, BigDecimal> sgdByCurrency = new TreeMap<>();
        for (PositionValue p : positions) {
            localByCurrency.merge(p.currency(), p.marketValueLocal(), BigDecimal::add);
            sgdByCurrency.merge(p.currency(), p.marketValueSgd(), BigDecimal::add);
        }
        return localByCurrency.keySet().stream()
                .map(c -> new CurrencyValue(c, localByCurrency.get(c), sgdByCurrency.get(c)))
                .toList();
    }

    /** Held positions only (BUY − SELL ≠ 0), ordered by ticker for stable output. */
    private List<HoldingRow> nonZeroHoldings() {
        return transactions.aggregateHoldings().stream()
                .filter(h -> h.getUnits().signum() != 0)
                .sorted(Comparator.comparing(HoldingRow::getTicker))
                .toList();
    }

    private Map<String, String> currencyByTicker(List<HoldingRow> rows) {
        List<String> tickers = rows.stream().map(HoldingRow::getTicker).toList();
        return instruments.findAllById(tickers).stream()
                .collect(Collectors.toMap(Instrument::getTicker, Instrument::getCurrency));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
