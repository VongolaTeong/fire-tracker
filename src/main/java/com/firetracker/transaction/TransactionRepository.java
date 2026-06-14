package com.firetracker.transaction;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * Of the supplied ids, which already exist in the ledger. Used by CSV import to skip
     * rows that were imported on an earlier run (dedup keyed on {@code external_id}).
     */
    @Query("select t.externalId from Transaction t where t.externalId in :ids")
    List<String> findExistingExternalIds(@Param("ids") Collection<String> ids);

    /**
     * Net units held per instrument, computed DB-side: BUY adds, SELL subtracts, and
     * DIVIDEND is ignored (a cash distribution, not a change in units). Returns one row per
     * ticker that has any transactions; callers drop fully-closed (zero-net) positions.
     */
    @Query("""
            select t.ticker as ticker,
                   coalesce(sum(case when t.type = com.firetracker.transaction.TransactionType.BUY  then t.quantity else 0 end), 0)
                 - coalesce(sum(case when t.type = com.firetracker.transaction.TransactionType.SELL then t.quantity else 0 end), 0) as units
            from Transaction t
            group by t.ticker
            """)
    List<HoldingRow> aggregateHoldings();

    /** The full ledger in chronological order — the cash-flow series for XIRR/CAGR. */
    List<Transaction> findAllByOrderByTransactionDateAscIdAsc();
}
