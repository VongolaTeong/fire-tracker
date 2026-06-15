package com.firetracker.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.projection.dto.ProjectionBand;
import com.firetracker.projection.dto.ProjectionResponse;
import com.firetracker.transaction.TransactionService;
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
 * Full-context projection tests against a real Postgres (Testcontainers) with a fixed clock and
 * fixed RNG seed, so the fan is deterministic. Proves Step 6's "done when": sensible percentile
 * bands out to the target date, the DCA rate inferred from the actual ledger (at transaction-date
 * FX), and a clean rejection of non-future targets.
 *
 * <p>The simulation math itself is pinned to closed-form references in {@link MonteCarloSimulationTest};
 * here we verify the wiring — starting capital, contribution inference, fan shape, determinism.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ProjectionServiceIntegrationTest {

    private static final String HEADER =
            "external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date";

    /** Pins "today" to 2025-06-15 so horizons and dates are reproducible. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    ProjectionService projection;

    @Autowired
    TransactionService transactions;

    @Autowired
    PriceHistoryRepository prices;

    @Autowired
    FxRateRepository fxRates;

    @Autowired
    ProjectionProperties props;

    @Test
    void projectsAYearlyFanAndInfersTheMonthlyContributionFromBuys() {
        // Two SGD buys a month apart: 1000 + 1200 = 2200 over 2 months => 1100/mo.
        transactions.importCsv(new java.io.StringReader(HEADER + "\n"
                + "a1,ACME,BUY,100,10.00,SGD,0.00,2025-01-10\n"
                + "a2,ACME,BUY,100,12.00,SGD,0.00,2025-02-10\n"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 6, 1), new BigDecimal("11.00"), "SGD"));

        ProjectionResponse p = projection.project(LocalDate.of(2030, 6, 15));

        assertThat(p.reportingCurrency()).isEqualTo("SGD");
        assertThat(p.asOf()).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(p.targetDate()).isEqualTo(LocalDate.of(2030, 6, 15));
        assertThat(p.months()).isEqualTo(60);
        assertThat(p.initialValue()).isEqualByComparingTo("2200.00"); // 200 units × 11.00
        assertThat(p.monthlyContribution()).isEqualByComparingTo("1100.00");
        assertThat(p.totalContributions()).isEqualByComparingTo("66000.00"); // 1100 × 60

        // Yearly checkpoints: now, +1y … +5y => 6 bands, ascending by date, each ordered.
        assertThat(p.bands()).hasSize(6);
        LocalDate previousDate = null;
        for (ProjectionBand band : p.bands()) {
            if (previousDate != null) {
                assertThat(band.date()).isAfter(previousDate);
            }
            previousDate = band.date();
            assertThat(band.p10()).isLessThanOrEqualTo(band.p50());
            assertThat(band.p50()).isLessThanOrEqualTo(band.p90());
        }

        // The fan starts as a collapsed point at the current value, today.
        ProjectionBand start = p.bands().get(0);
        assertThat(start.monthsFromNow()).isZero();
        assertThat(start.date()).isEqualTo(LocalDate.of(2025, 6, 15));
        assertThat(start.p10()).isEqualByComparingTo("2200.00");
        assertThat(start.p90()).isEqualByComparingTo("2200.00");

        // The terminal band is the target date and shows real uncertainty after five years of DCA.
        ProjectionBand terminal = p.bands().get(p.bands().size() - 1);
        assertThat(terminal.date()).isEqualTo(LocalDate.of(2030, 6, 15));
        assertThat(terminal.monthsFromNow()).isEqualTo(60);
        assertThat(terminal.p50()).isGreaterThan(p.initialValue());
        assertThat(terminal.p90()).isGreaterThan(terminal.p10());
    }

    @Test
    void infersTheContributionUsingTransactionDateFxNotTheLatestRate() {
        // USD buys when 1 USD = 1.30 SGD; the later 1.35 rate is only for current valuation.
        transactions.importCsv(new java.io.StringReader(HEADER + "\n"
                + "u1,GLOBL,BUY,10,100.00,USD,0.00,2025-01-15\n"
                + "u2,GLOBL,BUY,10,100.00,USD,0.00,2025-02-15\n"));
        fxRates.save(new FxRate(LocalDate.of(2025, 1, 1), "USD", "SGD", new BigDecimal("1.30")));
        fxRates.save(new FxRate(LocalDate.of(2025, 6, 1), "USD", "SGD", new BigDecimal("1.35")));
        prices.save(new PriceHistory("GLOBL", LocalDate.of(2025, 6, 1), new BigDecimal("100.00"), "USD"));

        ProjectionResponse p = projection.project(LocalDate.of(2030, 6, 15));

        // Each buy: 10 × 100 × 1.30 = 1300 SGD; 2600 over 2 months => 1300/mo (not 1.35).
        assertThat(p.monthlyContribution()).isEqualByComparingTo("1300.00");
        // Current value uses the latest 1.35 rate: 20 × 100 × 1.35 = 2700.
        assertThat(p.initialValue()).isEqualByComparingTo("2700.00");
    }

    @Test
    void fallsBackToTheConfiguredContributionWhenThereIsNoBuyHistory() {
        ProjectionResponse p = projection.project(LocalDate.of(2030, 6, 15));

        assertThat(p.initialValue()).isEqualByComparingTo("0.00");
        assertThat(p.monthlyContribution()).isEqualByComparingTo(props.getMonthlyContribution());
        assertThat(p.bands()).hasSize(6);
        // Starting from nothing, the median still grows on the back of ongoing contributions.
        assertThat(p.bands().get(p.bands().size() - 1).p50()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void rejectsATargetDateThatIsNotAtLeastAMonthInTheFuture() {
        assertThatThrownBy(() -> projection.project(LocalDate.of(2025, 6, 15))) // == today
                .isInstanceOf(InvalidProjectionRequestException.class);
        assertThatThrownBy(() -> projection.project(LocalDate.of(2025, 1, 1))) // in the past
                .isInstanceOf(InvalidProjectionRequestException.class);
    }

    @Test
    void producesAnIdenticalFanOnRepeatedCalls() {
        transactions.importCsv(new java.io.StringReader(HEADER + "\n"
                + "a1,ACME,BUY,100,10.00,SGD,0.00,2025-01-10\n"));
        prices.save(new PriceHistory("ACME", LocalDate.of(2025, 6, 1), new BigDecimal("11.00"), "SGD"));

        ProjectionResponse first = projection.project(LocalDate.of(2035, 6, 15));
        ProjectionResponse second = projection.project(LocalDate.of(2035, 6, 15));

        assertThat(second.bands()).isEqualTo(first.bands());
    }
}
