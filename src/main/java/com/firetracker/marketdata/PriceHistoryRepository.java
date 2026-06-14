package com.firetracker.marketdata;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    /** The most recent price for a ticker — what "current value" is computed against. */
    Optional<PriceHistory> findFirstByTickerOrderByPriceDateDesc(String ticker);
}
