package com.firetracker.transaction.dto;

import com.firetracker.transaction.Transaction;
import com.firetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** API view of a stored transaction. */
public record TransactionResponse(
        Long id,
        String ticker,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        String currency,
        BigDecimal fee,
        LocalDate transactionDate,
        String externalId,
        OffsetDateTime createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getTicker(),
                t.getType(),
                t.getQuantity(),
                t.getPricePerUnit(),
                t.getCurrency(),
                t.getFee(),
                t.getTransactionDate(),
                t.getExternalId(),
                t.getCreatedAt());
    }
}
