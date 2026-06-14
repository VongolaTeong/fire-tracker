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
}
