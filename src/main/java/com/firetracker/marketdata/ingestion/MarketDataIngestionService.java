package com.firetracker.marketdata.ingestion;

import com.firetracker.instrument.Instrument;
import com.firetracker.instrument.InstrumentRepository;
import com.firetracker.marketdata.FxRateRepository;
import com.firetracker.marketdata.MarketDataProperties;
import com.firetracker.marketdata.PriceHistoryRepository;
import com.firetracker.marketdata.provider.FxProvider;
import com.firetracker.marketdata.provider.FxQuote;
import com.firetracker.marketdata.provider.PriceProvider;
import com.firetracker.marketdata.provider.PriceQuote;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes {@code price_history} and {@code fx_rate} from the configured providers. For each
 * instrument it upserts the latest close (in the instrument's own currency); for each distinct
 * non-reporting currency it upserts the latest rate into the reporting currency. Writes go
 * through the repositories' {@code ON CONFLICT DO UPDATE} upserts, so the run is idempotent —
 * safe to repeat, with no duplicate rows.
 *
 * <p>Resilient by design: a missing quote skips just that instrument/currency (providers return
 * empty rather than throwing), so one bad feed never aborts the whole run.
 */
@Service
public class MarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionService.class);

    private final InstrumentRepository instruments;
    private final PriceHistoryRepository prices;
    private final FxRateRepository fxRates;
    private final PriceProvider priceProvider;
    private final FxProvider fxProvider;
    private final MarketDataProperties properties;

    public MarketDataIngestionService(InstrumentRepository instruments,
                                      PriceHistoryRepository prices,
                                      FxRateRepository fxRates,
                                      PriceProvider priceProvider,
                                      FxProvider fxProvider,
                                      MarketDataProperties properties) {
        this.instruments = instruments;
        this.prices = prices;
        this.fxRates = fxRates;
        this.priceProvider = priceProvider;
        this.fxProvider = fxProvider;
        this.properties = properties;
    }

    @Transactional
    public IngestionSummary ingest() {
        List<Instrument> all = instruments.findAll();

        int pricesUpserted = 0;
        for (Instrument instrument : all) {
            String symbol = properties.getPrice().getSymbols()
                    .getOrDefault(instrument.getTicker(), instrument.getTicker());
            Optional<PriceQuote> quote = priceProvider.fetchLatestPrice(symbol);
            if (quote.isPresent()) {
                PriceQuote q = quote.get();
                prices.upsert(instrument.getTicker(), q.date(), q.close(), instrument.getCurrency());
                pricesUpserted++;
            } else {
                log.info("No price quote for {} (symbol {})", instrument.getTicker(), symbol);
            }
        }

        String reporting = properties.getReportingCurrency();
        Set<String> currencies = new TreeSet<>();
        for (Instrument instrument : all) {
            if (!reporting.equalsIgnoreCase(instrument.getCurrency())) {
                currencies.add(instrument.getCurrency());
            }
        }

        int ratesUpserted = 0;
        for (String currency : currencies) {
            Optional<FxQuote> quote = fxProvider.fetchLatestRate(currency, reporting);
            if (quote.isPresent()) {
                FxQuote q = quote.get();
                fxRates.upsert(q.date(), currency, reporting, q.rate());
                ratesUpserted++;
            } else {
                log.info("No FX quote for {}->{}", currency, reporting);
            }
        }

        log.info("Market-data ingestion upserted {} price(s) and {} rate(s)", pricesUpserted, ratesUpserted);
        return new IngestionSummary(pricesUpserted, ratesUpserted);
    }
}
