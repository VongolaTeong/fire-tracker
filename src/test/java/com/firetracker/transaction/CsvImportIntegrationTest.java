package com.firetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.firetracker.TestcontainersConfiguration;
import com.firetracker.transaction.dto.ImportResult;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-context import tests against a real Postgres (Testcontainers). These prove Step 2's
 * "done when": importing the sample file twice yields the same row count, and a malformed
 * row rejects the whole file without inserting anything.
 *
 * <p>{@code @Transactional} rolls each test back so methods don't see each other's rows.
 * The import logic flushes before its dedup query, so within a single transaction the second
 * import still sees the first's inserts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CsvImportIntegrationTest {

    private static final String HEADER =
            "external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date";

    @Autowired
    TransactionService service;

    @Autowired
    TransactionRepository repository;

    @Test
    void importingTheSampleFileTwiceIsIdempotent() throws Exception {
        // The committed fake fixture — also proves the shipped sample imports cleanly.
        String csv = Files.readString(Path.of("sample-transactions.csv"));

        ImportResult first = service.importCsv(new StringReader(csv));
        assertThat(first.imported()).isGreaterThan(0);
        assertThat(first.skippedDuplicates()).isZero();
        assertThat(first.received()).isEqualTo(first.imported());
        long countAfterFirst = repository.count();
        assertThat(countAfterFirst).isEqualTo(first.imported());

        // Re-importing the identical file inserts nothing and leaves the row count unchanged.
        ImportResult second = service.importCsv(new StringReader(csv));
        assertThat(second.imported()).isZero();
        assertThat(second.skippedDuplicates()).isEqualTo(first.received());
        assertThat(repository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    void partialOverlapImportsOnlyTheNewRows() {
        service.importCsv(new StringReader(HEADER + "\n"
                + "vwra-1,VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"));
        assertThat(repository.count()).isEqualTo(1);

        // Re-send the existing row plus one new row: only the new one lands.
        ImportResult result = service.importCsv(new StringReader(HEADER + "\n"
                + "vwra-1,VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"
                + "vwra-2,VWRA,BUY,4.85,114.55,USD,1.00,2025-02-03\n"));

        assertThat(result.received()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void malformedRowRejectsTheWholeFileWithNothingInserted() {
        String csv = HEADER + "\n"
                + "vwra-1,VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"
                + "vwra-2,VWRA,NOPE,4.85,114.55,USD,1.00,2025-02-03\n"; // bad type on row 2

        assertThatThrownBy(() -> service.importCsv(new StringReader(csv)))
                .isInstanceOf(CsvImportException.class);

        // The valid first row must not have been persisted — import is all-or-nothing.
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsRowThatViolatesBusinessRules() {
        // Negative quantity passes CSV parsing but fails bean validation (@Positive).
        String csv = HEADER + "\n"
                + "vwra-1,VWRA,BUY,-5.0,110.20,USD,1.00,2025-01-06\n";

        assertThatThrownBy(() -> service.importCsv(new StringReader(csv)))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("quantity");
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsDuplicateExternalIdWithinFile() {
        String csv = HEADER + "\n"
                + "dup,VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"
                + "dup,VWRA,BUY,4.85,114.55,USD,1.00,2025-02-03\n";

        assertThatThrownBy(() -> service.importCsv(new StringReader(csv)))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("duplicate external_id");
        assertThat(repository.count()).isZero();
    }
}
