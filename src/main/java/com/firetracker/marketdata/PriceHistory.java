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
 * A daily close price for an instrument, in the instrument's own currency. Money is
 * {@link BigDecimal} backed by a NUMERIC column — never floating point. One row per
 * {@code (ticker, price_date)}; valuation reads the latest row per ticker.
 */
@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String ticker;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PriceHistory() {
        // for JPA
    }

    public PriceHistory(String ticker, LocalDate priceDate, BigDecimal closePrice, String currency) {
        this.ticker = ticker;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getTicker() {
        return ticker;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
