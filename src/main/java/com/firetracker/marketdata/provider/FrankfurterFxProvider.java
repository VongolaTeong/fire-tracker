package com.firetracker.marketdata.provider;

import com.firetracker.marketdata.MarketDataProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link FxProvider} backed by <a href="https://frankfurter.dev">Frankfurter</a> — free ECB
 * reference rates, no API key. Calls {@code GET /latest?base={base}&symbols={quote}} and reads
 * the published date and the single requested rate. Any transport/parse failure yields an
 * empty result (logged), never an exception, so the ingestion job stays resilient.
 */
@Component
public class FrankfurterFxProvider implements FxProvider {

    private static final Logger log = LoggerFactory.getLogger(FrankfurterFxProvider.class);

    private final RestClient restClient;

    public FrankfurterFxProvider(RestClient.Builder builder, MarketDataProperties properties) {
        this.restClient = builder.baseUrl(properties.getFx().getBaseUrl()).build();
    }

    @Override
    public Optional<FxQuote> fetchLatestRate(String base, String quote) {
        try {
            LatestResponse body = restClient.get()
                    .uri(uri -> uri.path("/latest")
                            .queryParam("base", base)
                            .queryParam("symbols", quote)
                            .build())
                    .retrieve()
                    .body(LatestResponse.class);

            if (body == null || body.date() == null || body.rates() == null) {
                return Optional.empty();
            }
            BigDecimal rate = body.rates().get(quote);
            return rate == null ? Optional.empty() : Optional.of(new FxQuote(body.date(), rate));
        } catch (RestClientException e) {
            log.warn("FX fetch failed for {}->{}: {}", base, quote, e.toString());
            return Optional.empty();
        }
    }

    /** Subset of the Frankfurter response we use; unknown fields (amount, base) are ignored. */
    private record LatestResponse(LocalDate date, Map<String, BigDecimal> rates) {
    }
}
