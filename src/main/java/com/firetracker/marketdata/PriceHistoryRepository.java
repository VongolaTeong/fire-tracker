package com.firetracker.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    /** The most recent price for a ticker — what "current value" is computed against. */
    Optional<PriceHistory> findFirstByTickerOrderByPriceDateDesc(String ticker);

    /**
     * Insert a price, or refresh it in place if one already exists for this
     * {@code (ticker, price_date)} — a real upsert keyed on the business unique constraint,
     * not JPA's PK-based merge. This is what makes the ingestion job idempotent: re-running it
     * updates the close price rather than inserting a duplicate.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into price_history (ticker, price_date, close_price, currency)
            values (:ticker, :priceDate, :closePrice, :currency)
            on conflict (ticker, price_date)
            do update set close_price = excluded.close_price,
                          currency    = excluded.currency
            """, nativeQuery = true)
    void upsert(@Param("ticker") String ticker,
                @Param("priceDate") LocalDate priceDate,
                @Param("closePrice") BigDecimal closePrice,
                @Param("currency") String currency);
}
