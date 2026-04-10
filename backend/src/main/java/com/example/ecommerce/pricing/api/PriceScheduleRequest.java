package com.example.ecommerce.pricing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceScheduleRequest(
    @NotNull(message = "listPrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "listPrice must be positive")
    BigDecimal listPrice,
    @NotNull(message = "salePrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "salePrice must be positive")
    BigDecimal salePrice,
    @NotNull(message = "effectiveAt is required")
    LocalDateTime effectiveAt,
    LocalDateTime expireAt,
    String reason,
    Long operatorId
) {
}
