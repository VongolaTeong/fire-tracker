package com.firetracker.projection;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.firetracker.portfolio.MissingMarketDataException;
import com.firetracker.projection.dto.ProjectionBand;
import com.firetracker.projection.dto.ProjectionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice: routing, date-param binding, JSON serialization of the fan, and error
 * mapping with the service mocked. The simulation math is proven in {@link MonteCarloSimulationTest}
 * and the end-to-end wiring in {@link ProjectionServiceIntegrationTest}.
 */
@WebMvcTest(ProjectionController.class)
class ProjectionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProjectionService service;

    @Test
    void returnsTheProjectionFan() throws Exception {
        ProjectionResponse response = new ProjectionResponse(
                "SGD", LocalDate.of(2026, 6, 15), LocalDate.of(2028, 6, 15), 24,
                new BigDecimal("10000.00"), new BigDecimal("1000.00"), new BigDecimal("24000.00"),
                0.07, 0.15, 10_000,
                List.of(
                        new ProjectionBand(LocalDate.of(2026, 6, 15), 0,
                                new BigDecimal("10000.00"), new BigDecimal("10000.00"), new BigDecimal("10000.00")),
                        new ProjectionBand(LocalDate.of(2028, 6, 15), 24,
                                new BigDecimal("28000.00"), new BigDecimal("36000.00"), new BigDecimal("45000.00"))));
        when(service.project(LocalDate.of(2028, 6, 15))).thenReturn(response);

        mockMvc.perform(get("/api/portfolio/projection").param("targetDate", "2028-06-15"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportingCurrency").value("SGD"))
                .andExpect(jsonPath("$.targetDate").value("2028-06-15"))
                .andExpect(jsonPath("$.months").value(24))
                .andExpect(jsonPath("$.monthlyContribution").value(1000.00))
                .andExpect(jsonPath("$.bands.length()").value(2))
                .andExpect(jsonPath("$.bands[0].date").value("2026-06-15"))
                .andExpect(jsonPath("$.bands[0].monthsFromNow").value(0))
                .andExpect(jsonPath("$.bands[1].date").value("2028-06-15"))
                .andExpect(jsonPath("$.bands[1].p10").value(28000.00))
                .andExpect(jsonPath("$.bands[1].p50").value(36000.00))
                .andExpect(jsonPath("$.bands[1].p90").value(45000.00));
    }

    @Test
    void returns400WhenTargetDateIsNotInTheFuture() throws Exception {
        when(service.project(ArgumentMatchers.any()))
                .thenThrow(new InvalidProjectionRequestException(
                        "targetDate must be at least one month after 2026-06-15, was 2026-01-01"));

        mockMvc.perform(get("/api/portfolio/projection").param("targetDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid projection request"));
    }

    @Test
    void returns400WhenTargetDateParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/portfolio/projection"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns422WhenMarketDataMissing() throws Exception {
        when(service.project(ArgumentMatchers.any()))
                .thenThrow(new MissingMarketDataException("No price available for VWRA"));

        mockMvc.perform(get("/api/portfolio/projection").param("targetDate", "2040-06-15"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Missing market data"));
    }
}
