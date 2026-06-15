package com.firetracker.marketdata.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.firetracker.marketdata.MarketDataProperties;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Proves the Frankfurter response parsing against canned JSON via {@link MockRestServiceServer}
 * — no Spring context, no live API. The mock is bound to the same {@code RestClient.Builder}
 * the provider builds its client from, so requests are intercepted in-process.
 */
class FrankfurterFxProviderTest {

    private MockRestServiceServer server;
    private FrankfurterFxProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FrankfurterFxProvider(builder, new MarketDataProperties());
    }

    @Test
    void parsesDateAndRequestedRate() {
        server.expect(requestTo(containsString("/latest")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("base", "USD"))
                .andExpect(queryParam("symbols", "SGD"))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2024-06-14\",\"rates\":{\"SGD\":1.35}}",
                        MediaType.APPLICATION_JSON));

        Optional<FxQuote> quote = provider.fetchLatestRate("USD", "SGD");

        assertThat(quote).isPresent();
        assertThat(quote.get().date()).isEqualTo(LocalDate.of(2024, 6, 14));
        assertThat(quote.get().rate()).isEqualByComparingTo("1.35");
        server.verify();
    }

    @Test
    void returnsEmptyWhenTheRequestedRateIsAbsent() {
        server.expect(requestTo(containsString("/latest")))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2024-06-14\",\"rates\":{}}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.fetchLatestRate("USD", "SGD")).isEmpty();
    }

    @Test
    void returnsEmptyOnTransportError() {
        server.expect(requestTo(containsString("/latest")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(provider.fetchLatestRate("USD", "SGD")).isEmpty();
    }
}
