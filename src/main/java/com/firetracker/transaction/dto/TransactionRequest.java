package com.firetracker.transaction.dto;

import com.firetracker.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload for creating a transaction. {@code fee} and {@code externalId} are optional. */
public record TransactionRequest(

        @NotBlank @Size(max = 32)
        String ticker,

        @NotNull
        TransactionType type,

        @NotNull @jakarta.validation.constraints.Positive
        BigDecimal quantity,

        @NotNull @PositiveOrZero
        BigDecimal pricePerUnit,

        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code")
        String currency,

        @PositiveOrZero
        BigDecimal fee,

        @NotNull @PastOrPresent
        LocalDate transactionDate,

        @Size(max = 128)
        String externalId
) {
}
