package com.firetracker.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An instrument the ledger can transact in (e.g. an ETF or stock), keyed by ticker.
 * Reference data — distinct from the {@code transaction} ledger that points at it.
 */
@Entity
@Table(name = "instrument")
public class Instrument {

    @Id
    @Column(length = 32)
    private String ticker;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstrumentType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Instrument() {
        // for JPA
    }

    public Instrument(String ticker, String name, String currency, InstrumentType type) {
        this.ticker = ticker;
        this.name = name;
        this.currency = currency;
        this.type = type;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public InstrumentType getType() {
        return type;
    }

    public void setType(InstrumentType type) {
        this.type = type;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
