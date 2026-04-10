package com.example.ecommerce.pricing.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceUpdateRequest(
    @NotNull(message = "listPrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "listPrice must be positive")
    BigDecimal listPrice,
    @NotNull(message = "salePrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "salePrice must be positive")
    BigDecimal salePrice,
    String reason,
    Long operatorId
) {
}
