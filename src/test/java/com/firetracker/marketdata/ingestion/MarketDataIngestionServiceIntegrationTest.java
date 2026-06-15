package com.firetracker.marketdata.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.instrument.Instrument;
import com.firetracker.instrument.InstrumentRepository;
import com.firetracker.instrument.InstrumentType;
import com.firetracker.marketdata.FxRate;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.PriceHistory;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.marketdata.provider.FxProvider;
import com.firetracker.marketdata.provider.FxQuote;
import com.firetracker.marketdata.provider.PriceProvider;
import com.firetracker.marketdata.provider.PriceQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-context ingestion tests against a real Postgres (Testcontainers), with the market-data
 * providers mocked so nothing leaves the JVM. Proves Step 5's "done when": the job upserts
 * price/FX rows and is safe to re-run — no duplicates, values refreshed.
 *
 * <p>Tickers here are deliberately outside the {@code application.yml} symbol map, so the feed
 * symbol falls back to the ticker and the stubs don't couple to that config.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MarketDataIngestionServiceIntegrationTest {

    private static final LocalDate QUOTE_DATE = LocalDate.of(2025, 6, 2);

    @Autowired
    MarketDataIngestionService ingestion;

    @Autowired
    InstrumentRepository instruments;

    @Autowired
    PriceHistoryRepository prices;

    @Autowired
    FxRateRepository fxRates;

    @MockitoBean
    PriceProvider priceProvider;

    @MockitoBean
    FxProvider fxProvider;

    private void seedInstruments() {
        instruments.save(new Instrument("ACME", "Acme Global ETF", "USD", InstrumentType.ETF));
        instruments.save(new Instrument("LOCAL", "Local Index ETF", "SGD", InstrumentType.ETF));
    }

    @Test
    void upsertsLatestPricesAndFxRatesAndSkipsTheReportingCurrency() {
        seedInstruments();
        when(priceProvider.fetchLatestPrice("ACME"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("130.00"))));
        when(priceProvider.fetchLatestPrice("LOCAL"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("3.50"))));
        when(fxProvider.fetchLatestRate("USD", "SGD"))
                .thenReturn(Optional.of(new FxQuote(QUOTE_DATE, new BigDecimal("1.35"))));

        IngestionSummary summary = ingestion.ingest();

        assertThat(summary.pricesUpserted()).isEqualTo(2);
        assertThat(summary.ratesUpserted()).isEqualTo(1);
        assertThat(prices.count()).isEqualTo(2);
        assertThat(fxRates.count()).isEqualTo(1);

        PriceHistory acme = prices.findFirstByTickerOrderByPriceDateDesc("ACME").orElseThrow();
        assertThat(acme.getClosePrice()).isEqualByComparingTo("130.00");
        assertThat(acme.getCurrency()).isEqualTo("USD"); // currency comes from the instrument
        assertThat(acme.getPriceDate()).isEqualTo(QUOTE_DATE);

        FxRate usd = fxRates
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc("USD", "SGD").orElseThrow();
        assertThat(usd.getRate()).isEqualByComparingTo("1.35");

        // The SGD instrument needs no conversion, so no FX lookup is made for it.
        verify(fxProvider, never()).fetchLatestRate(eq("SGD"), anyString());
    }

    @Test
    void rerunningIsIdempotentAndRefreshesValuesInPlace() {
        seedInstruments();
        when(priceProvider.fetchLatestPrice("ACME"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("130.00"))));
        when(priceProvider.fetchLatestPrice("LOCAL"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("3.50"))));
        when(fxProvider.fetchLatestRate("USD", "SGD"))
                .thenReturn(Optional.of(new FxQuote(QUOTE_DATE, new BigDecimal("1.35"))));

        ingestion.ingest();
        long pricesAfterFirst = prices.count();
        long ratesAfterFirst = fxRates.count();

        // Same quotes again: re-running writes nothing new (ON CONFLICT, same keys).
        ingestion.ingest();
        assertThat(prices.count()).isEqualTo(pricesAfterFirst);
        assertThat(fxRates.count()).isEqualTo(ratesAfterFirst);

        // New values for the same dates: counts stay put, but the rows are updated in place.
        when(priceProvider.fetchLatestPrice("ACME"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("140.00"))));
        when(fxProvider.fetchLatestRate("USD", "SGD"))
                .thenReturn(Optional.of(new FxQuote(QUOTE_DATE, new BigDecimal("1.40"))));

        ingestion.ingest();

        assertThat(prices.count()).isEqualTo(pricesAfterFirst);
        assertThat(fxRates.count()).isEqualTo(ratesAfterFirst);
        assertThat(prices.findFirstByTickerOrderByPriceDateDesc("ACME").orElseThrow().getClosePrice())
                .isEqualByComparingTo("140.00");
        assertThat(fxRates.findFirstByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc("USD", "SGD")
                .orElseThrow().getRate())
                .isEqualByComparingTo("1.40");
    }

    @Test
    void skipsInstrumentsWithoutAQuoteWithoutFailingTheRun() {
        seedInstruments();
        // ACME has a quote; LOCAL does not (unstubbed @MockitoBean returns Optional.empty()).
        when(priceProvider.fetchLatestPrice("ACME"))
                .thenReturn(Optional.of(new PriceQuote(QUOTE_DATE, new BigDecimal("130.00"))));
        when(fxProvider.fetchLatestRate("USD", "SGD"))
                .thenReturn(Optional.of(new FxQuote(QUOTE_DATE, new BigDecimal("1.35"))));

        IngestionSummary summary = ingestion.ingest();

        assertThat(summary.pricesUpserted()).isEqualTo(1);
        assertThat(prices.count()).isEqualTo(1);
        assertThat(prices.findFirstByTickerOrderByPriceDateDesc("LOCAL")).isEmpty();
    }
}
