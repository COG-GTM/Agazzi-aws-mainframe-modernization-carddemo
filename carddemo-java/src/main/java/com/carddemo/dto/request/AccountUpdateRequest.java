package com.carddemo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record AccountUpdateRequest(
        String activeStatus,

        @DecimalMin(value = "0.00", message = "Credit limit must be non-negative")
        BigDecimal creditLimit,

        @DecimalMin(value = "0.00", message = "Cash credit limit must be non-negative")
        BigDecimal cashCreditLimit
) {}
