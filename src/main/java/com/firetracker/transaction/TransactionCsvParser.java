package com.firetracker.transaction;

import com.firetracker.transaction.dto.TransactionRequest;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Turns the import CSV into typed {@link TransactionRequest} rows. This layer owns
 * <em>syntax</em>: the header shape, presence of required cells, and parsing numbers, dates
 * and the transaction-type enum. Business rules (positive quantity, ISO currency, dedup) are
 * left to {@link TransactionService}. Any structural problem is reported as a
 * {@link CsvImportException} prefixed with the offending data-row number.
 *
 * <p>Expected header (order-independent):
 * {@code external_id,ticker,type,quantity,price_per_unit,currency,fee,transaction_date}.
 * {@code fee} may be blank (treated as 0); every other column requires a value.
 */
@Component
public class TransactionCsvParser {

    public static final List<String> REQUIRED_HEADERS = List.of(
            "external_id", "ticker", "type", "quantity",
            "price_per_unit", "currency", "fee", "transaction_date");

    /** A single parsed data row, tagged with its 1-based data-row number for error messages. */
    public record ParsedRow(long line, String externalId, TransactionRequest request) {
    }

    public List<ParsedRow> parse(Reader reader) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .build();
        try (CSVParser parser = CSVParser.parse(reader, format)) {
            validateHeaders(parser.getHeaderNames());
            List<ParsedRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(toRow(record));
            }
            if (rows.isEmpty()) {
                throw new CsvImportException("CSV has a header but no data rows");
            }
            return rows;
        } catch (IOException e) {
            // I/O on the supplied reader is not a user data error — surface it as such.
            throw new UncheckedIOException(e);
        }
    }

    private void validateHeaders(List<String> headers) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(h -> !headers.contains(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new CsvImportException("Missing required column(s): " + String.join(", ", missing));
        }
    }

    private ParsedRow toRow(CSVRecord record) {
        long line = record.getRecordNumber();
        String externalId = required(record, "external_id", line);
        String ticker = required(record, "ticker", line);
        TransactionType type = parseType(required(record, "type", line), line);
        BigDecimal quantity = parseDecimal(required(record, "quantity", line), "quantity", line);
        BigDecimal pricePerUnit = parseDecimal(required(record, "price_per_unit", line), "price_per_unit", line);
        String currency = required(record, "currency", line);
        BigDecimal fee = parseFee(record.get("fee"), line);
        LocalDate transactionDate = parseDate(required(record, "transaction_date", line), line);

        TransactionRequest request = new TransactionRequest(
                ticker, type, quantity, pricePerUnit, currency, fee, transactionDate, externalId);
        return new ParsedRow(line, externalId, request);
    }

    private String required(CSVRecord record, String field, long line) {
        String value = record.get(field);
        if (value == null || value.isBlank()) {
            throw new CsvImportException("Row " + line + ": missing value for '" + field + "'");
        }
        return value;
    }

    private TransactionType parseType(String value, long line) {
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new CsvImportException("Row " + line + ": unknown type '" + value + "'");
        }
    }

    private BigDecimal parseDecimal(String value, String field, long line) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new CsvImportException("Row " + line + ": invalid number for '" + field + "': '" + value + "'");
        }
    }

    /** {@code fee} is optional; a blank cell means no fee. */
    private BigDecimal parseFee(String value, long line) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return parseDecimal(value, "fee", line);
    }

    private LocalDate parseDate(String value, long line) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CsvImportException(
                    "Row " + line + ": invalid date for 'transaction_date' (expected YYYY-MM-DD): '" + value + "'");
        }
    }
}
