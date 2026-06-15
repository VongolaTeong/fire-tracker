package com.firetracker.marketdata;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for market-data ingestion: which feeds to call, how to map an instrument
 * ticker to a feed-specific symbol, the reporting currency rates are pulled against, and the
 * daily schedule. All overridable via {@code app.market-data.*} (environment variables in
 * production); defaults point at free, keyless feeds.
 */
@ConfigurationProperties(prefix = "app.market-data")
public class MarketDataProperties {

    /** Currency every FX rate is pulled against (1 instrument-currency = rate of this). */
    private String reportingCurrency = "SGD";

    /** Cron for the daily ingestion job (UTC). Default: 18:00 daily. */
    private String cron = "0 0 18 * * *";

    private final Fx fx = new Fx();
    private final Price price = new Price();
    private final Scheduling scheduling = new Scheduling();

    public String getReportingCurrency() {
        return reportingCurrency;
    }

    public void setReportingCurrency(String reportingCurrency) {
        this.reportingCurrency = reportingCurrency;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Fx getFx() {
        return fx;
    }

    public Price getPrice() {
        return price;
    }

    public Scheduling getScheduling() {
        return scheduling;
    }

    /** FX feed settings (Frankfurter by default — ECB rates, no API key). */
    public static class Fx {
        private String baseUrl = "https://api.frankfurter.dev/v1";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /** Price feed settings (Stooq by default — free CSV, no API key). */
    public static class Price {
        private String baseUrl = "https://stooq.com/q/l/";

        /** Maps an instrument ticker to the feed's symbol (e.g. {@code VWRA -> vwra.uk}). */
        private Map<String, String> symbols = new HashMap<>();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Map<String, String> getSymbols() {
            return symbols;
        }

        public void setSymbols(Map<String, String> symbols) {
            this.symbols = symbols;
        }
    }

    /** Whether the scheduled job runs; off unless explicitly enabled per environment. */
    public static class Scheduling {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
