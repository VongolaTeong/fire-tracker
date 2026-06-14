package com.firetracker.portfolio;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.firetracker.portfolio.dto.CurrencyValue;
import com.firetracker.portfolio.dto.HoldingResponse;
import com.firetracker.portfolio.dto.PortfolioValueResponse;
import com.firetracker.portfolio.dto.PositionValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice: routing, JSON serialization, and the {@code @RestControllerAdvice} error
 * mapping with the service mocked. Exact valuation math is proven in
 * {@link PortfolioServiceIntegrationTest}; here we just check the wiring.
 */
@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortfolioService service;

    @Test
    void listsHoldings() throws Exception {
        when(service.holdings()).thenReturn(List.of(
                new HoldingResponse("VWRA", new BigDecimal("15.000000"), "USD")));

        mockMvc.perform(get("/api/portfolio/holdings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].ticker").value("VWRA"))
                .andExpect(jsonPath("$[0].units").exists())
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void returnsPortfolioValueWithBreakdown() throws Exception {
        PortfolioValueResponse response = new PortfolioValueResponse(
                "SGD", new BigDecimal("3712.50"),
                List.of(new PositionValue("VWRA", new BigDecimal("15"), "USD",
                        new BigDecimal("130.00"), LocalDate.of(2025, 8, 15),
                        new BigDecimal("1950.00"), new BigDecimal("1.35"), new BigDecimal("2632.50"))),
                List.of(new CurrencyValue("USD", new BigDecimal("1950.00"), new BigDecimal("2632.50"))));
        when(service.value()).thenReturn(response);

        mockMvc.perform(get("/api/portfolio/value"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportingCurrency").value("SGD"))
                .andExpect(jsonPath("$.totalValueSgd").exists())
                .andExpect(jsonPath("$.positions[0].ticker").value("VWRA"))
                .andExpect(jsonPath("$.positions[0].currency").value("USD"))
                .andExpect(jsonPath("$.byCurrency[0].currency").value("USD"));
    }

    @Test
    void returns422WhenMarketDataMissing() throws Exception {
        when(service.value())
                .thenThrow(new MissingMarketDataException("No price available for VWRA"));

        mockMvc.perform(get("/api/portfolio/value"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Missing market data"))
                .andExpect(jsonPath("$.detail").value("No price available for VWRA"));
    }
}
