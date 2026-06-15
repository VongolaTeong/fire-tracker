package com.firetracker.marketdata.provider;

import com.firetracker.marketdata.MarketDataProperties;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link PriceProvider} backed by <a href="https://stooq.com">Stooq</a>'s free CSV quote
 * endpoint, no API key. Calls {@code GET /?s={symbol}&f=sd2t2ohlcv&h&e=csv}, which returns a
 * one-row CSV ({@code Symbol,Date,Time,Open,High,Low,Close,Volume}); we read {@code Date} and
 * {@code Close}. Stooq reports {@code N/D} for an unknown symbol or before the close prints —
 * that, like any transport/parse failure, yields an empty result (logged) rather than throwing.
 */
@Component
public class StooqPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(StooqPriceProvider.class);
    private static final String NO_DATA = "N/D";

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

    private final RestClient restClient;

    public StooqPriceProvider(RestClient.Builder builder, MarketDataProperties properties) {
        this.restClient = builder.baseUrl(properties.getPrice().getBaseUrl()).build();
    }

    @Override
    public Optional<PriceQuote> fetchLatestPrice(String symbol) {
        try {
            String csv = restClient.get()
                    .uri(uri -> uri.queryParam("s", symbol)
                            .queryParam("f", "sd2t2ohlcv")
                            .queryParam("h", "")
                            .queryParam("e", "csv")
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(csv, symbol);
        } catch (RestClientException e) {
            log.warn("Price fetch failed for {}: {}", symbol, e.toString());
            return Optional.empty();
        }
    }

    private Optional<PriceQuote> parse(String csv, String symbol) {
        if (csv == null || csv.isBlank()) {
            return Optional.empty();
        }
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSV)) {
            for (CSVRecord record : parser) {
                String date = record.get("Date");
                String close = record.get("Close");
                if (NO_DATA.equalsIgnoreCase(date) || NO_DATA.equalsIgnoreCase(close)) {
                    return Optional.empty(); // feed has no quote for this symbol yet
                }
                return Optional.of(new PriceQuote(LocalDate.parse(date), new BigDecimal(close)));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not parse Stooq response for {}: {}", symbol, e.toString());
            return Optional.empty();
        }
    }
}
