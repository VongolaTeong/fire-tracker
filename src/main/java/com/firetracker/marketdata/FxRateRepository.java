package com.firetracker.marketdata;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
