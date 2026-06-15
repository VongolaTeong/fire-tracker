package com.firetracker.marketdata.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs {@link MarketDataIngestionService} on a daily cron. Gated behind
 * {@code app.market-data.scheduling.enabled} (off by default), so it never fires in dev, CI, or
 * tests — production opts in via {@code APP_MARKET_DATA_SCHEDULING_ENABLED=true}. The ingestion
 * logic itself is covered by direct tests, independent of the schedule.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.market-data.scheduling.enabled", havingValue = "true")
public class MarketDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataScheduler.class);

    private final MarketDataIngestionService ingestion;

    public MarketDataScheduler(MarketDataIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Scheduled(cron = "${app.market-data.cron:0 0 18 * * *}", zone = "UTC")
    public void ingestDaily() {
        log.info("Running scheduled market-data ingestion");
        ingestion.ingest();
    }
}
