package com.carddemo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BillPaymentRequest(
        @NotNull(message = "Account ID is required")
        Long acctId,

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be positive")
        BigDecimal amount
) {}
