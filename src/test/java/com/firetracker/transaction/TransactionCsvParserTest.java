package com.firetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.firetracker.transaction.TransactionCsvParser.ParsedRow;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CSV <em>syntax</em>: header shape, required cells, and type parsing. No
 * Spring context or database — the parser is a plain component, so these run fast.
 */
class TransactionCsvParserTest {

    private static final String HEADER =
            "external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date";

    private final TransactionCsvParser parser = new TransactionCsvParser();

    private List<ParsedRow> parse(String csv) {
        return parser.parse(new StringReader(csv));
    }

    @Test
    void parsesValidRowsIntoTypedRequests() {
        List<ParsedRow> rows = parse(HEADER + "\n"
                + "vwra-1,VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"
                + "vwra-2,VWRA,DIVIDEND,28.65,0.42,USD,,2025-06-20\n");

        assertThat(rows).hasSize(2);

        ParsedRow first = rows.get(0);
        assertThat(first.line()).isEqualTo(1);
        assertThat(first.externalId()).isEqualTo("vwra-1");
        assertThat(first.request().ticker()).isEqualTo("VWRA");
        assertThat(first.request().type()).isEqualTo(TransactionType.BUY);
        assertThat(first.request().quantity()).isEqualByComparingTo("5.0");
        assertThat(first.request().pricePerUnit()).isEqualByComparingTo("110.20");
        assertThat(first.request().fee()).isEqualByComparingTo("1.00");

        // A blank fee cell is treated as zero rather than rejected.
        assertThat(rows.get(1).request().fee()).isEqualByComparingTo("0");
    }

    @Test
    void columnOrderDoesNotMatter() {
        List<ParsedRow> rows = parse(
                "type,external_id,transaction_date,ticker,fee,currency,price_per_unit,quantity\n"
                        + "BUY,vwra-1,2025-01-06,VWRA,1.00,USD,110.20,5.0\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).request().ticker()).isEqualTo("VWRA");
        assertThat(rows.get(0).request().quantity()).isEqualByComparingTo("5.0");
    }

    @Test
    void rejectsMissingRequiredColumn() {
        // No price_per_unit column at all.
        String csv = "external_id,ticker,type,quantity,currency,fee,transaction_date\n"
                + "vwra-1,VWRA,BUY,5.0,USD,1.00,2025-01-06\n";

        assertThatThrownBy(() -> parse(csv))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("price_per_unit");
    }

    @Test
    void rejectsUnknownTransactionType() {
        assertThatThrownBy(() -> parse(HEADER + "\n"
                + "vwra-1,VWRA,NOPE,5.0,110.20,USD,1.00,2025-01-06\n"))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("Row 1")
                .hasMessageContaining("NOPE");
    }

    @Test
    void rejectsUnparseableNumber() {
        assertThatThrownBy(() -> parse(HEADER + "\n"
                + "vwra-1,VWRA,BUY,five,110.20,USD,1.00,2025-01-06\n"))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsBadDate() {
        assertThatThrownBy(() -> parse(HEADER + "\n"
                + "vwra-1,VWRA,BUY,5.0,110.20,USD,1.00,06/01/2025\n"))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("transaction_date");
    }

    @Test
    void rejectsMissingRequiredValue() {
        // Blank external_id cell.
        assertThatThrownBy(() -> parse(HEADER + "\n"
                + ",VWRA,BUY,5.0,110.20,USD,1.00,2025-01-06\n"))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("external_id");
    }

    @Test
    void rejectsHeaderWithNoDataRows() {
        assertThatThrownBy(() -> parse(HEADER + "\n"))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("no data rows");
    }
}
