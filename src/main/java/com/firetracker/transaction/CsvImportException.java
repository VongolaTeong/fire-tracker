package com.firetracker.transaction;

/**
 * Raised when a CSV import cannot be processed — a missing/extra column, an unparseable
 * value, a row that fails validation, or a duplicate {@code external_id} within the file.
 * Carries a human-readable message (typically prefixed with the offending row) and maps to
 * HTTP 400. The import is all-or-nothing: this is thrown before any row is persisted, so a
 * rejected file never leaves a partial insert behind.
 */
public class CsvImportException extends RuntimeException {

    public CsvImportException(String message) {
        super(message);
    }
}
