package com.firetracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.performance.dto.PerformanceResponse;
import com.firetracker.portfolio.dto.CurrencyValue;
import com.firetracker.portfolio.dto.HoldingResponse;
import com.firetracker.portfolio.dto.PortfolioValueResponse;
import com.firetracker.portfolio.dto.ValuePoint;
import com.firetracker.projection.dto.ProjectionBand;
import com.firetracker.projection.dto.ProjectionResponse;
import com.firetracker.transaction.dto.ImportResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The Step 8 coverage sweep: one test that drives the whole pipeline — import → holdings →
 * value → performance → projection — end to end over real HTTP (MockMvc) against a real
 * Postgres (Testcontainers), on the shipped fake fixtures ({@code sample-transactions.csv}
 * plus the {@code seed.sql} price/FX numbers).
 *
 * <p>Where the per-service integration tests call the services directly with inline ledgers,
 * this exercises the full stack a client actually hits: routing, JSON (de)serialization, the
 * {@link com.firetracker.common.GlobalExceptionHandler} mapping, and cross-endpoint
 * consistency (performance {@code currentValue} reconciles to the value endpoint's total).
 *
 * <p>MockMvc dispatches on the test thread, so each controller's {@code @Transactional} joins
 * this test's transaction and reads see the rows just imported; {@code @Transactional} then
 * rolls everything back, keeping the methods isolated — the same mechanism every sibling
 * integration test relies on.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EndToEndFlowIntegrationTest {

    /** Pins "today" to 2025-09-15 — just after the latest seeded price/FX date (2025-08-15). */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-09-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PriceHistoryRepository prices;

    @Autowired
    FxRateRepository fxRates;

    @Test
    void fullPipelineFromImportThroughProjection() throws Exception {
        // ---- import: the shipped sample ledger lands in full, nothing skipped ----
        ImportResult imported = importSampleCsv();
        assertThat(imported.received()).isEqualTo(12);
        assertThat(imported.imported()).isEqualTo(12);
        assertThat(imported.skippedDuplicates()).isZero();

        seedMarketData();

        // ---- holdings: BUY − SELL per instrument, DIVIDEND ignored, ticker-ordered ----
        List<HoldingResponse> holdings = getJson("/api/portfolio/holdings", new TypeReference<>() {});
        assertThat(holdings).extracting(HoldingResponse::ticker).containsExactly("ES3", "VWRA");
        assertThat(holding(holdings, "ES3").units()).isEqualByComparingTo("890");   // 300 + 280 + 310
        assertThat(holding(holdings, "ES3").currency()).isEqualTo("SGD");
        assertThat(holding(holdings, "VWRA").units()).isEqualByComparingTo("37.20"); // Σ buys; dividend ignored
        assertThat(holding(holdings, "VWRA").currency()).isEqualTo("USD");

        // ---- value: latest price + latest FX, SGD total, per-currency breakdown reconciles ----
        PortfolioValueResponse value = getJson("/api/portfolio/value", PortfolioValueResponse.class);
        assertThat(value.reportingCurrency()).isEqualTo("SGD");
        // VWRA: 37.20 × 129.50 USD = 4817.40 × 1.35 = 6503.49; ES3: 890 × 3.52 = 3132.80.
        assertThat(value.totalValueSgd()).isEqualByComparingTo("9636.29");
        assertThat(value.byCurrency()).extracting(CurrencyValue::currency).containsExactly("SGD", "USD");
        assertThat(currency(value, "USD").marketValueSgd()).isEqualByComparingTo("6503.49");
        assertThat(currency(value, "SGD").marketValueSgd()).isEqualByComparingTo("3132.80");
        BigDecimal breakdownTotal = value.byCurrency().stream()
                .map(CurrencyValue::marketValueSgd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(breakdownTotal).isEqualByComparingTo(value.totalValueSgd());

        // ---- value-history: a non-empty series, ascending by date, ending with real value ----
        List<ValuePoint> history = getJson("/api/portfolio/value-history", new TypeReference<>() {});
        assertThat(history).isNotEmpty();
        assertThat(history).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
        assertThat(history.get(history.size() - 1).valueSgd()).isPositive();

        // ---- performance: figures present, and currentValue reconciles to the value endpoint ----
        PerformanceResponse perf = getJson("/api/portfolio/performance", PerformanceResponse.class);
        assertThat(perf.reportingCurrency()).isEqualTo("SGD");
        assertThat(perf.asOf()).isEqualTo(LocalDate.of(2025, 9, 15));
        assertThat(perf.totalInvested()).isPositive();
        assertThat(perf.currentValue()).isEqualByComparingTo(value.totalValueSgd()); // cross-endpoint invariant
        assertThat(perf.unrealizedPnl())
                .isEqualByComparingTo(perf.currentValue().subtract(perf.totalInvested()));
        assertThat(perf.xirr()).isNotNull();
        assertThat(perf.cagr()).isNotNull();

        // ---- projection: a fan that starts collapsed at today's value and widens with order ----
        ProjectionResponse projection = getJson(
                "/api/portfolio/projection?targetDate=2040-09-15", ProjectionResponse.class);
        assertThat(projection.asOf()).isEqualTo(LocalDate.of(2025, 9, 15));
        assertThat(projection.targetDate()).isEqualTo(LocalDate.of(2040, 9, 15));
        assertThat(projection.initialValue()).isEqualByComparingTo(value.totalValueSgd());
        assertThat(projection.bands()).hasSizeGreaterThan(1);

        ProjectionBand start = projection.bands().get(0);
        assertThat(start.monthsFromNow()).isZero();
        assertThat(start.date()).isEqualTo(LocalDate.of(2025, 9, 15));
        assertThat(start.p10()).isEqualByComparingTo(projection.initialValue());
        assertThat(start.p90()).isEqualByComparingTo(projection.initialValue());

        LocalDate previousDate = null;
        for (ProjectionBand band : projection.bands()) {
            if (previousDate != null) {
                assertThat(band.date()).isAfter(previousDate);
            }
            previousDate = band.date();
            assertThat(band.p10()).isLessThanOrEqualTo(band.p50());
            assertThat(band.p50()).isLessThanOrEqualTo(band.p90());
        }

        ProjectionBand terminal = projection.bands().get(projection.bands().size() - 1);
        assertThat(terminal.date()).isEqualTo(LocalDate.of(2040, 9, 15));
        assertThat(terminal.p50()).isGreaterThan(projection.initialValue()); // grows over 15 years of DCA
        assertThat(terminal.p90()).isGreaterThan(terminal.p10());            // and shows real uncertainty
    }

    @Test
    void reimportingTheSampleCsvIsANoOp() throws Exception {
        ImportResult first = importSampleCsv();
        assertThat(first.imported()).isEqualTo(12);

        // Re-uploading the identical file inserts nothing — idempotency end to end over HTTP.
        ImportResult second = importSampleCsv();
        assertThat(second.received()).isEqualTo(12);
        assertThat(second.imported()).isZero();
        assertThat(second.skippedDuplicates()).isEqualTo(12);

        List<HoldingResponse> holdings = getJson("/api/portfolio/holdings", new TypeReference<>() {});
        assertThat(holding(holdings, "VWRA").units()).isEqualByComparingTo("37.20");
        assertThat(holding(holdings, "ES3").units()).isEqualByComparingTo("890");
    }

    @Test
    void valuationReturns422WhenMarketDataIsMissing() throws Exception {
        // Import the ledger but seed no prices/FX: the portfolio can't be valued yet.
        importSampleCsv();

        mockMvc.perform(get("/api/portfolio/value"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Missing market data"));
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** POSTs the committed {@code sample-transactions.csv} to the import endpoint as multipart. */
    private ImportResult importSampleCsv() throws Exception {
        byte[] csv = Files.readAllBytes(Path.of("sample-transactions.csv"));
        MockMultipartFile file = new MockMultipartFile("file", "sample-transactions.csv", "text/csv", csv);

        MvcResult result = mockMvc.perform(multipart("/api/transactions/import").file(file))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ImportResult.class);
    }

    /**
     * Seeds the price and FX rows from {@code seed.sql} (kept in sync with that file). The ledger
     * itself arrives via the CSV import, so only market data is seeded here. Two prices and several
     * FX rows so "latest wins" current valuation and as-of cost-basis FX are both real.
     */
    private void seedMarketData() {
        prices.save(new PriceHistory("VWRA", LocalDate.of(2025, 7, 15), new BigDecimal("126.80"), "USD"));
        prices.save(new PriceHistory("VWRA", LocalDate.of(2025, 8, 15), new BigDecimal("129.50"), "USD"));
        prices.save(new PriceHistory("ES3", LocalDate.of(2025, 7, 15), new BigDecimal("3.49"), "SGD"));
        prices.save(new PriceHistory("ES3", LocalDate.of(2025, 8, 15), new BigDecimal("3.52"), "SGD"));

        fxRates.save(new FxRate(LocalDate.of(2025, 1, 6), "USD", "SGD", new BigDecimal("1.36")));
        fxRates.save(new FxRate(LocalDate.of(2025, 2, 3), "USD", "SGD", new BigDecimal("1.35")));
        fxRates.save(new FxRate(LocalDate.of(2025, 3, 3), "USD", "SGD", new BigDecimal("1.345")));
        fxRates.save(new FxRate(LocalDate.of(2025, 4, 7), "USD", "SGD", new BigDecimal("1.335")));
        fxRates.save(new FxRate(LocalDate.of(2025, 5, 5), "USD", "SGD", new BigDecimal("1.33")));
        fxRates.save(new FxRate(LocalDate.of(2025, 6, 2), "USD", "SGD", new BigDecimal("1.335")));
        fxRates.save(new FxRate(LocalDate.of(2025, 7, 15), "USD", "SGD", new BigDecimal("1.34")));
        fxRates.save(new FxRate(LocalDate.of(2025, 8, 15), "USD", "SGD", new BigDecimal("1.35")));
    }

    private <T> T getJson(String url, Class<T> type) throws Exception {
        return objectMapper.readValue(okBody(url), type);
    }

    private <T> T getJson(String url, TypeReference<T> type) throws Exception {
        return objectMapper.readValue(okBody(url), type);
    }

    private String okBody(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static HoldingResponse holding(List<HoldingResponse> holdings, String ticker) {
        return holdings.stream().filter(h -> h.ticker().equals(ticker)).findFirst().orElseThrow();
    }

    private static CurrencyValue currency(PortfolioValueResponse value, String currency) {
        return value.byCurrency().stream()
                .filter(c -> c.currency().equals(currency)).findFirst().orElseThrow();
    }
}
