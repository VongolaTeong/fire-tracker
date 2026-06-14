package com.firetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.transaction.dto.TransactionRequest;
import com.firetracker.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full-context integration test against a real Postgres (Testcontainers). Booting the
 * context also validates the Flyway schema against the JPA entities. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransactionPersistenceTests {

    @Autowired
    TransactionService service;

    @Autowired
    TransactionRepository repository;

    @Test
    void savesReadsBackAndFiltersTransactions() {
        TransactionRequest request = new TransactionRequest(
                "VWRA", TransactionType.BUY, new BigDecimal("3.5"),
                new BigDecimal("100.25"), "USD", new BigDecimal("1.00"),
                LocalDate.of(2026, 1, 15), "ext-001");

        TransactionResponse created = service.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();

        // Round-trip: reload straight from the repository and check BigDecimal/enum mapping.
        TransactionResponse reloaded = repository.findById(created.id())
                .map(TransactionResponse::from)
                .orElseThrow();
        assertThat(reloaded.ticker()).isEqualTo("VWRA");
        assertThat(reloaded.type()).isEqualTo(TransactionType.BUY);
        assertThat(reloaded.quantity()).isEqualByComparingTo("3.5");
        assertThat(reloaded.pricePerUnit()).isEqualByComparingTo("100.25");
        assertThat(reloaded.fee()).isEqualByComparingTo("1.00");
        assertThat(reloaded.currency()).isEqualTo("USD");

        // Filters: matching ticker + date range returns the row; misses return empty.
        assertThat(service.search("VWRA", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .hasSize(1);
        assertThat(service.search("NOPE", null, null)).isEmpty();

        List<TransactionResponse> all = service.search(null, null, null);
        assertThat(all).extracting(TransactionResponse::ticker).contains("VWRA");
    }
}
