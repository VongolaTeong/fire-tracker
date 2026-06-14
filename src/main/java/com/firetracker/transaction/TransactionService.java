package com.firetracker.transaction;

import com.firetracker.instrument.Instrument;
import com.firetracker.instrument.InstrumentRepository;
import com.firetracker.instrument.InstrumentType;
import com.firetracker.transaction.dto.TransactionRequest;
import com.firetracker.transaction.dto.TransactionResponse;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final InstrumentRepository instruments;

    public TransactionService(TransactionRepository transactions, InstrumentRepository instruments) {
        this.transactions = transactions;
        this.instruments = instruments;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        ensureInstrumentExists(req);

        Transaction t = new Transaction();
        t.setTicker(req.ticker());
        t.setType(req.type());
        t.setQuantity(req.quantity());
        t.setPricePerUnit(req.pricePerUnit());
        t.setCurrency(req.currency());
        t.setFee(req.fee() != null ? req.fee() : BigDecimal.ZERO);
        t.setTransactionDate(req.transactionDate());
        t.setExternalId(req.externalId());

        return TransactionResponse.from(transactions.save(t));
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
     * A transaction references an instrument by ticker (FK). If the instrument isn't known
     * yet, provision a minimal stub from the transaction's own fields; name and type can be
     * enriched later via a dedicated instrument flow.
     */
    private void ensureInstrumentExists(TransactionRequest req) {
        if (!instruments.existsById(req.ticker())) {
            instruments.save(new Instrument(req.ticker(), req.ticker(), req.currency(), InstrumentType.ETF));
        }
    }
}
