package com.carddemo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransactionAddRequest(
        @NotBlank(message = "Card number is required")
        @Size(min = 16, max = 16, message = "Card number must be 16 characters")
        String cardNum,

        @NotBlank(message = "Transaction type code is required")
        @Size(min = 2, max = 2, message = "Transaction type code must be 2 characters")
        String tranTypeCd,

        @NotNull(message = "Transaction category code is required")
        Integer tranCatCd,

        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "0.01", message = "Transaction amount must be positive")
        BigDecimal tranAmt,

        String tranSource,
        String tranDesc,
        Long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip
) {}
