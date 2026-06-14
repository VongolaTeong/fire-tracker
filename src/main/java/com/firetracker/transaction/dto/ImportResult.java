package com.firetracker.transaction.dto;

/**
 * Outcome of a bulk CSV import.
 *
 * @param received          data rows read from the file (excludes the header)
 * @param imported          rows newly inserted this run
 * @param skippedDuplicates rows whose {@code external_id} already existed, so they were
 *                          skipped — this is what makes re-running the same file a no-op
 */
public record ImportResult(int received, int imported, int skippedDuplicates) {
}
