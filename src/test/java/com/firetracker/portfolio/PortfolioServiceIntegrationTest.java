package com.firetracker.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.portfolio.dto.CurrencyValue;
import com.firetracker.portfolio.dto.HoldingResponse;
import com.firetracker.portfolio.dto.PortfolioValueResponse;
import com.firetracker.portfolio.dto.PositionValue;
import com.firetracker.portfolio.dto.ValuePoint;
import com.firetracker.transaction.TransactionService;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-context valuation tests against a real Postgres (Testcontainers). Proves Step 3's
 * "done when": holdings aggregation and SGD valuation return correct figures against seeded
 * data, including the multi-currency (USD + SGD) case.
 *
 * <p>The ledger is seeded through {@link TransactionService#importCsv} (which also provisions
 * the instrument rows), and price/FX rows through the market-data repositories.
 * {@code @Transactional} rolls each test back; the valuation queries auto-flush, so they see
 * the rows written earlier in the same test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PortfolioServiceIntegrationTest {

    private static final String HEADER =
            "external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date";

    /** Pins "today" to 2025-03-31 so the value-history month-end checkpoints are reproducible. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-03-31T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    PortfolioService portfolio;

    @Autowired
    TransactionService transactions;

    @Autowired
    PriceHistoryRepository prices;

    @Autowired
    FxRateRepository fxRates;

    @Test
    void holdingsNetBuysAgainstSellsAndIgnoreDividends() {
        seedLedger();

        List<HoldingResponse> holdings = portfolio.holdings();

        // SOLD is fully closed (BUY 10, SELL 10) so it is omitted; output is ticker-ordered.
        assertThat(holdings).extracting(HoldingResponse::ticker).containsExactly("ES3", "VWRA");

        HoldingResponse vwra = holding(holdings, "VWRA");
        assertThat(vwra.units()).isEqualByComparingTo("15"); // 10 + 5; DIVIDEND(15) ignored
        assertThat(vwra.currency()).isEqualTo("USD");

        HoldingResponse es3 = holding(holdings, "ES3");
        assertThat(es3.units()).isEqualByComparingTo("300"); // 200 + 100
        assertThat(es3.currency()).isEqualTo("SGD");
    }

    @Test
    void valuesHoldingsInSgdWithPerCurrencyBreakdown() {
        seedLedger();
        seedMarketData();

        PortfolioValueResponse value = portfolio.value();

        assertThat(value.reportingCurrency()).isEqualTo("SGD");
        // VWRA: 15 × 130.00 USD = 1950.00 × 1.35 = 2632.50 SGD; ES3: 300 × 3.60 = 1080.00 SGD.
        assertThat(value.totalValueSgd()).isEqualByComparingTo("3712.50");

        PositionValue vwra = position(value, "VWRA");
        assertThat(vwra.price()).isEqualByComparingTo("130.00"); // latest price wins over 125.00
        assertThat(vwra.priceDate()).isEqualTo(LocalDate.of(2025, 8, 15));
        assertThat(vwra.fxRate()).isEqualByComparingTo("1.35"); // latest rate wins over 1.30
        assertThat(vwra.marketValueLocal()).isEqualByComparingTo("1950.00");
        assertThat(vwra.marketValueSgd()).isEqualByComparingTo("2632.50");

        PositionValue es3 = position(value, "ES3");
        assertThat(es3.fxRate()).isEqualByComparingTo("1"); // already SGD — converts at 1
        assertThat(es3.marketValueLocal()).isEqualByComparingTo("1080.00");
        assertThat(es3.marketValueSgd()).isEqualByComparingTo("1080.00");

        // Per-currency breakdown reconciles to the total and is ordered by currency code.
        assertThat(value.byCurrency()).extracting(CurrencyValue::currency).containsExactly("SGD", "USD");
        CurrencyValue usd = currencyValue(value, "USD");
        assertThat(usd.marketValueLocal()).isEqualByComparingTo("1950.00");
        assertThat(usd.marketValueSgd()).isEqualByComparingTo("2632.50");
        CurrencyValue sgd = currencyValue(value, "SGD");
        assertThat(sgd.marketValueLocal()).isEqualByComparingTo("1080.00");
        assertThat(sgd.marketValueSgd()).isEqualByComparingTo("1080.00");
    }

    @Test
    void valueFailsWhenAHeldInstrumentHasNoPrice() {
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "n1,NOPRICE,BUY,5,10.00,USD,1.00,2025-01-06\n"));

        assertThatThrownBy(() -> portfolio.value())
                .isInstanceOf(MissingMarketDataException.class)
                .hasMessageContaining("NOPRICE");
    }

    @Test
    void valueFailsWhenFxRateMissing() {
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "u1,USDONLY,BUY,5,10.00,USD,1.00,2025-01-06\n"));
        prices.save(new PriceHistory("USDONLY", LocalDate.of(2025, 8, 15), new BigDecimal("12.00"), "USD"));
        // No USD->SGD rate seeded: the position has a price but cannot be converted.

        assertThatThrownBy(() -> portfolio.value())
                .isInstanceOf(MissingMarketDataException.class)
                .hasMessageContaining("USD->SGD");
    }

    @Test
    void valueHistoryValuesEachMonthEndAsOfThatDate() {
        // One SGD instrument (no FX): 100 units from Jan, +50 from Mar.
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "a1,ACME,BUY,100,10.00,SGD,0.00,2025-01-10\n"
                + "a2,ACME,BUY,50,12.00,SGD,0.00,2025-03-10\n"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 1, 15), new BigDecimal("10.50"), "SGD"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 2, 20), new BigDecimal("11.00"), "SGD"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 3, 25), new BigDecimal("12.00"), "SGD"));

        List<ValuePoint> series = portfolio.valueHistory();

        // Month-ends from Jan through Mar (today = 2025-03-31, exactly the March month-end).
        assertThat(series).extracting(ValuePoint::date).containsExactly(
                LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 31));
        assertThat(series.get(0).valueSgd()).isEqualByComparingTo("1050.00"); // 100 × 10.50 (as-of Jan)
        assertThat(series.get(1).valueSgd()).isEqualByComparingTo("1100.00"); // 100 × 11.00 (as-of Feb)
        assertThat(series.get(2).valueSgd()).isEqualByComparingTo("1800.00"); // 150 × 12.00 (as-of Mar)
    }

    @Test
    void valueHistorySkipsPositionsNotYetPriceableAndUsesAsOfFx() {
        // USD position bought before any price/FX exists; data only appears from February.
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "u1,GLOBL,BUY,10,100.00,USD,0.00,2025-01-10\n"));
        prices.save(new PriceHistory("GLOBL", LocalDate.of(2025, 2, 10), new BigDecimal("100.00"), "USD"));
        prices.save(new PriceHistory("GLOBL", LocalDate.of(2025, 3, 10), new BigDecimal("110.00"), "USD"));
        fxRates.save(new FxRate(LocalDate.of(2025, 2, 5), "USD", "SGD", new BigDecimal("1.30")));
        fxRates.save(new FxRate(LocalDate.of(2025, 3, 5), "USD", "SGD", new BigDecimal("1.35")));

        List<ValuePoint> series = portfolio.valueHistory();

        assertThat(series).hasSize(3);
        assertThat(series.get(0).valueSgd()).isEqualByComparingTo("0.00");    // Jan: no price yet
        assertThat(series.get(1).valueSgd()).isEqualByComparingTo("1300.00"); // 10 × 100 × 1.30
        assertThat(series.get(2).valueSgd()).isEqualByComparingTo("1485.00"); // 10 × 110 × 1.35
    }

    @Test
    void valueHistoryIsEmptyForAnEmptyLedger() {
        assertThat(portfolio.valueHistory()).isEmpty();
    }

    private void seedLedger() {
        transactions.importCsv(new StringReader(String.join("\n",
                HEADER,
                "b1,VWRA,BUY,10,100.00,USD,1.00,2025-01-06",
                "b2,VWRA,BUY,5,120.00,USD,1.00,2025-02-06",
                "d1,VWRA,DIVIDEND,15,0.50,USD,0.00,2025-03-06",
                "b3,ES3,BUY,200,3.00,SGD,2.00,2025-02-12",
                "b4,ES3,BUY,100,3.50,SGD,2.00,2025-05-12",
                "x1,SOLD,BUY,10,50.00,USD,1.00,2025-01-10",
                "x2,SOLD,SELL,10,55.00,USD,1.00,2025-04-10") + "\n"));
    }

    private void seedMarketData() {
        // Two VWRA prices and two FX rates so "latest wins" is actually exercised.
        prices.save(new PriceHistory("VWRA", LocalDate.of(2025, 7, 15), new BigDecimal("125.00"), "USD"));
        prices.save(new PriceHistory("VWRA", LocalDate.of(2025, 8, 15), new BigDecimal("130.00"), "USD"));
        prices.save(new PriceHistory("ES3", LocalDate.of(2025, 8, 15), new BigDecimal("3.60"), "SGD"));
        fxRates.save(new FxRate(LocalDate.of(2025, 7, 15), "USD", "SGD", new BigDecimal("1.30")));
        fxRates.save(new FxRate(LocalDate.of(2025, 8, 15), "USD", "SGD", new BigDecimal("1.35")));
    }

    private static HoldingResponse holding(List<HoldingResponse> holdings, String ticker) {
        return holdings.stream()
                .filter(h -> h.ticker().equals(ticker))
                .findFirst().orElseThrow();
    }

    private static PositionValue position(PortfolioValueResponse value, String ticker) {
        return value.positions().stream()
                .filter(p -> p.ticker().equals(ticker))
                .findFirst().orElseThrow();
    }

    private static CurrencyValue currencyValue(PortfolioValueResponse value, String currency) {
        return value.byCurrency().stream()
                .filter(c -> c.currency().equals(currency))
                .findFirst().orElseThrow();
    }
}
