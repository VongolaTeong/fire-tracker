package com.firetracker.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.performance.dto.PerformanceResponse;
import com.firetracker.portfolio.MissingMarketDataException;
import com.firetracker.transaction.TransactionService;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-context performance tests against a real Postgres (Testcontainers) with a fixed clock,
 * so XIRR/CAGR are deterministic. Proves Step 4's "done when": correct figures against seeded
 * data, with cost-basis FX (transaction-date) kept distinct from current-value FX (latest).
 *
 * <p>The algorithm itself is pinned to external references in {@link XirrTest}; here we verify
 * the end-to-end wiring and the FX-correctness that only the full ledger path exercises.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PerformanceServiceIntegrationTest {

    private static final String HEADER =
            "external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date";

    /** Pins "today" to 2025-06-15 so the time-dependent analytics are reproducible. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    PerformanceService performance;

    @Autowired
    TransactionService transactions;

    @Autowired
    PriceHistoryRepository prices;

    @Autowired
    FxRateRepository fxRates;

    @Test
    void computesInvestedValuePnlXirrAndCagrForASgdPortfolio() {
        // Single SGD buy a year before "today"; no FX involved (rate 1).
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "a1,ACME,BUY,100,10.00,SGD,0.00,2024-06-15\n"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 6, 1), new BigDecimal("11.00"), "SGD"));

        PerformanceResponse perf = performance.performance();

        assertThat(perf.reportingCurrency()).isEqualTo("SGD");
        assertThat(perf.asOf()).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(perf.totalInvested()).isEqualByComparingTo("1000.00"); // 100 × 10
        assertThat(perf.currentValue()).isEqualByComparingTo("1100.00");  // 100 × 11
        assertThat(perf.unrealizedPnl()).isEqualByComparingTo("100.00");
        // Exactly one year apart (2024-06-15 → 2025-06-15 = 365 days): +10% both ways.
        assertThat(perf.xirr().doubleValue()).isCloseTo(0.10, within(1e-4));
        assertThat(perf.cagr().doubleValue()).isCloseTo(0.10, within(1e-4));
    }

    @Test
    void costBasisUsesTransactionDateFxWhileCurrentValueUsesLatestFx() {
        // USD buy on 2025-01-15 when 1 USD = 1.30 SGD; latest rate is 1.35 SGD.
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "u1,GLOBL,BUY,10,100.00,USD,0.00,2025-01-15\n"));
        fxRates.save(new FxRate(LocalDate.of(2025, 1, 1), "USD", "SGD", new BigDecimal("1.30")));
        fxRates.save(new FxRate(LocalDate.of(2025, 6, 1), "USD", "SGD", new BigDecimal("1.35")));
        prices.save(new PriceHistory("GLOBL", LocalDate.of(2025, 6, 1), new BigDecimal("100.00"), "USD"));

        PerformanceResponse perf = performance.performance();

        // Cost basis converts at the transaction-date rate 1.30: 10 × 100 × 1.30 = 1300.
        assertThat(perf.totalInvested()).isEqualByComparingTo("1300.00");
        // Current value converts at the latest rate 1.35: 10 × 100 × 1.35 = 1350.
        assertThat(perf.currentValue()).isEqualByComparingTo("1350.00");
        assertThat(perf.unrealizedPnl()).isEqualByComparingTo("50.00");
    }

    @Test
    void failsWhenATransactionHasNoFxRateOnOrBeforeItsDate() {
        // USD buy but the only FX row is dated after the transaction — no as-of rate exists.
        transactions.importCsv(new StringReader(HEADER + "\n"
                + "u1,GLOBL,BUY,10,100.00,USD,0.00,2025-01-15\n"));
        fxRates.save(new FxRate(LocalDate.of(2025, 6, 1), "USD", "SGD", new BigDecimal("1.35")));
        prices.save(new PriceHistory("GLOBL", LocalDate.of(2025, 6, 1), new BigDecimal("100.00"), "USD"));

        assertThatThrownBy(() -> performance.performance())
                .isInstanceOf(MissingMarketDataException.class)
                .hasMessageContaining("USD->SGD");
    }

    @Test
    void emptyPortfolioReportsZerosAndNullRates() {
        PerformanceResponse perf = performance.performance();

        assertThat(perf.totalInvested()).isEqualByComparingTo("0.00");
        assertThat(perf.currentValue()).isEqualByComparingTo("0.00");
        assertThat(perf.unrealizedPnl()).isEqualByComparingTo("0.00");
        assertThat(perf.xirr()).isNull();
        assertThat(perf.cagr()).isNull();
    }
}
