package com.firetracker.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.firetracker.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test: exercises routing, JSON (de)serialization, and bean validation
 * with the service mocked. Needs no database, so it runs without Docker.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionService service;

    @Test
    void createsTransactionAndReturns201WithLocation() throws Exception {
        TransactionResponse response = new TransactionResponse(
                1L, "VWRA", TransactionType.BUY, new BigDecimal("3.500000"),
                new BigDecimal("100.250000"), "USD", new BigDecimal("1.000000"),
                LocalDate.of(2026, 1, 15), null, OffsetDateTime.now());
        when(service.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "VWRA",
                                  "type": "BUY",
                                  "quantity": 3.5,
                                  "pricePerUnit": 100.25,
                                  "currency": "USD",
                                  "fee": 1.0,
                                  "transactionDate": "2026-01-15"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/transactions/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ticker").value("VWRA"))
                .andExpect(jsonPath("$.type").value("BUY"));
    }

    @Test
    void rejectsInvalidPayloadWith400AndFieldErrors() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticker": "",
                                  "type": "BUY",
                                  "quantity": -5,
                                  "pricePerUnit": 100.25,
                                  "currency": "usd",
                                  "transactionDate": "2026-01-15"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ticker").exists())
                .andExpect(jsonPath("$.errors.quantity").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void listsTransactions() throws Exception {
        when(service.search(null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
