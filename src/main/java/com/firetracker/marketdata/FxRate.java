package com.firetracker.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A daily FX rate for a currency pair. Convention: {@code 1 baseCurrency = rate quoteCurrency}
 * (e.g. base USD, quote SGD, rate 1.35 means 1 USD = 1.35 SGD). One row per
 * {@code (rate_date, base_currency, quote_currency)}; valuation reads the latest row per pair.
 */
@Entity
@Table(name = "fx_rate")
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected FxRate() {
        // for JPA
    }

    public FxRate(LocalDate rateDate, String baseCurrency, String quoteCurrency, BigDecimal rate) {
        this.rateDate = rateDate;
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
