package com.firetracker.transaction;

import com.firetracker.instrument.Instrument;
import com.firetracker.instrument.InstrumentRepository;
import com.firetracker.instrument.InstrumentType;
import com.firetracker.transaction.TransactionCsvParser.ParsedRow;
import com.firetracker.transaction.dto.ImportResult;
import com.firetracker.transaction.dto.TransactionRequest;
import com.firetracker.transaction.dto.TransactionResponse;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final InstrumentRepository instruments;
    private final TransactionCsvParser csvParser;
    private final Validator validator;

    public TransactionService(TransactionRepository transactions,
                              InstrumentRepository instruments,
                              TransactionCsvParser csvParser,
                              Validator validator) {
        this.transactions = transactions;
        this.instruments = instruments;
        this.csvParser = csvParser;
        this.validator = validator;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        ensureInstrumentExists(req.ticker(), req.currency());
        return TransactionResponse.from(transactions.save(toEntity(req)));
    }

    /**
     * Bulk-import a transaction ledger from CSV. The import is idempotent: rows are keyed by
     * {@code external_id}, and any whose id already exists in the ledger are skipped, so
     * re-running the same file never double-inserts.
     *
     * <p>All-or-nothing on bad input: the whole file is parsed and validated before a single
     * row is written, so a malformed row rejects the import without leaving a partial insert.
     */
    @Transactional
    public ImportResult importCsv(Reader reader) {
        List<ParsedRow> rows = csvParser.parse(reader);
        validate(rows);

        // Dedup against rows already in the ledger, resolved in one query rather than per row.
        Set<String> externalIds = rows.stream().map(ParsedRow::externalId).collect(Collectors.toSet());
        Set<String> existing = new HashSet<>(transactions.findExistingExternalIds(externalIds));

        int imported = 0;
        for (ParsedRow row : rows) {
            if (!existing.add(row.externalId())) {
                continue; // already in the ledger (or, defensively, earlier in this batch)
            }
            TransactionRequest req = row.request();
            ensureInstrumentExists(req.ticker(), req.currency());
            transactions.save(toEntity(req));
            imported++;
        }
        return new ImportResult(rows.size(), imported, rows.size() - imported);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionResponse> findById(Long id) {
        return transactions.findById(id).map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> search(String ticker, LocalDate from, LocalDate to) {
        // Build the filter dynamically: a null argument simply omits that predicate, so
        // absent filters never reach the SQL (avoids untyped "? is null" parameters).
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ticker != null) {
                predicates.add(cb.equal(root.get("ticker"), ticker));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("transactionDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"));
        return transactions.findAll(spec, sort).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * Apply the same bean-validation constraints the REST endpoint enforces to every imported
     * row, and reject a file that repeats an {@code external_id} (ambiguous — which row wins?).
     * Runs over the whole batch before any insert so the import stays all-or-nothing.
     */
    private void validate(List<ParsedRow> rows) {
        Set<String> seen = new HashSet<>();
        for (ParsedRow row : rows) {
            Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(row.request());
            if (!violations.isEmpty()) {
                String detail = violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
                throw new CsvImportException("Row " + row.line() + ": " + detail);
            }
            if (!seen.add(row.externalId())) {
                throw new CsvImportException(
                        "Row " + row.line() + ": duplicate external_id '" + row.externalId() + "' within file");
            }
        }
    }

    private Transaction toEntity(TransactionRequest req) {
        Transaction t = new Transaction();
        t.setTicker(req.ticker());
        t.setType(req.type());
        t.setQuantity(req.quantity());
        t.setPricePerUnit(req.pricePerUnit());
        t.setCurrency(req.currency());
        t.setFee(req.fee() != null ? req.fee() : BigDecimal.ZERO);
        t.setTransactionDate(req.transactionDate());
        t.setExternalId(req.externalId());
        return t;
    }

    /**
     * A transaction references an instrument by ticker (FK). If the instrument isn't known
     * yet, provision a minimal stub from the transaction's own fields; name and type can be
     * enriched later via a dedicated instrument flow.
     */
    private void ensureInstrumentExists(String ticker, String currency) {
        if (!instruments.existsById(ticker)) {
            instruments.save(new Instrument(ticker, ticker, currency, InstrumentType.ETF));
        }
    }
}
