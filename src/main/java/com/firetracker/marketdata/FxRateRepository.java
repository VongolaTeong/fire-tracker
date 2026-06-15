package com.firetracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /** The most recent rate for a currency pair — what current holdings are converted at. */
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(
            String baseCurrency, String quoteCurrency);

    /**
     * The rate for a currency pair as-of a given date: the latest row on or before it. Used
     * for cost-basis conversion, where each transaction is valued at the FX rate that applied
     * on its transaction date (distinct from the latest rate used for current value).
     */
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
            String baseCurrency, String quoteCurrency, LocalDate rateDate);

    /**
     * Insert a rate, or refresh it in place if one already exists for this
     * {@code (rate_date, base_currency, quote_currency)} — a real upsert keyed on the business
     * unique constraint. Makes the ingestion job idempotent: re-running updates the rate
     * rather than inserting a duplicate.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into fx_rate (rate_date, base_currency, quote_currency, rate)
            values (:rateDate, :baseCurrency, :quoteCurrency, :rate)
            on conflict (rate_date, base_currency, quote_currency)
            do update set rate = excluded.rate
            """, nativeQuery = true)
    void upsert(@Param("rateDate") LocalDate rateDate,
                @Param("baseCurrency") String baseCurrency,
                @Param("quoteCurrency") String quoteCurrency,
                @Param("rate") BigDecimal rate);
}
