package com.firetracker.performance;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.firetracker.performance.dto.PerformanceResponse;
import com.firetracker.portfolio.MissingMarketDataException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice: routing, JSON serialization, and the error mapping with the service
 * mocked. The valuation/return math is proven in {@link PerformanceServiceIntegrationTest}.
 */
@WebMvcTest(PerformanceController.class)
class PerformanceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PerformanceService service;

    @Test
    void returnsPerformanceSummary() throws Exception {
        PerformanceResponse response = new PerformanceResponse(
                "SGD", LocalDate.of(2026, 6, 15),
                new BigDecimal("1000.00"), new BigDecimal("1100.00"), new BigDecimal("100.00"),
                new BigDecimal("0.100000"), new BigDecimal("0.100000"));
        when(service.performance()).thenReturn(response);

        mockMvc.perform(get("/api/portfolio/performance"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportingCurrency").value("SGD"))
                .andExpect(jsonPath("$.asOf").value("2026-06-15"))
                .andExpect(jsonPath("$.totalInvested").exists())
                .andExpect(jsonPath("$.unrealizedPnl").exists())
                .andExpect(jsonPath("$.xirr").exists())
                .andExpect(jsonPath("$.cagr").exists());
    }

    @Test
    void returns422WhenMarketDataMissing() throws Exception {
        when(service.performance())
                .thenThrow(new MissingMarketDataException(
                        "No FX rate available for USD->SGD on or before 2025-01-15"));

        mockMvc.perform(get("/api/portfolio/performance"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Missing market data"));
    }
}
