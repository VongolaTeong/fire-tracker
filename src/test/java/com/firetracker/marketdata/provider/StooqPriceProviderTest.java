package com.firetracker.marketdata.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.firetracker.marketdata.MarketDataProperties;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Proves the Stooq CSV parsing against canned responses via {@link MockRestServiceServer} —
 * no Spring context, no live API.
 */
class StooqPriceProviderTest {

    private static final String HEADER = "Symbol,Date,Time,Open,High,Low,Close,Volume";

    private MockRestServiceServer server;
    private StooqPriceProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new StooqPriceProvider(builder, new MarketDataProperties());
    }

    @Test
    void parsesDateAndClose() {
        server.expect(requestTo(containsString("/q/l")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("s", "vwra.uk"))
                .andRespond(withSuccess(
                        HEADER + "\nVWRA.UK,2024-06-14,21:00:09,108.5,109,108,108.70,12345\n",
                        MediaType.valueOf("text/csv")));

        Optional<PriceQuote> quote = provider.fetchLatestPrice("vwra.uk");

        assertThat(quote).isPresent();
        assertThat(quote.get().date()).isEqualTo(LocalDate.of(2024, 6, 14));
        assertThat(quote.get().close()).isEqualByComparingTo("108.70");
        server.verify();
    }

    @Test
    void returnsEmptyWhenStooqReportsNoData() {
        // Stooq returns N/D for an unknown symbol or before the close has printed.
        server.expect(requestTo(containsString("/q/l")))
                .andRespond(withSuccess(
                        HEADER + "\nNOPE.US,N/D,N/D,N/D,N/D,N/D,N/D,N/D\n",
                        MediaType.valueOf("text/csv")));

        assertThat(provider.fetchLatestPrice("nope.us")).isEmpty();
    }
}
