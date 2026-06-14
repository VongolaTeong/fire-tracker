package com.firetracker.marketdata;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /** The most recent rate for a currency pair — what current holdings are converted at. */
    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByRateDateDesc(
            String baseCurrency, String quoteCurrency);
}
