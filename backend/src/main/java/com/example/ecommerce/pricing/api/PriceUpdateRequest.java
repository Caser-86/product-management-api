package com.example.ecommerce.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceUpdateRequest(
    @Schema(description = "List price", example = "189.00")
    @NotNull(message = "listPrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "listPrice must be positive")
    BigDecimal listPrice,
    @Schema(description = "Sale price", example = "149.00")
    @NotNull(message = "salePrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "salePrice must be positive")
    BigDecimal salePrice,
    @Schema(description = "Reason for the manual change", example = "weekend campaign")
    String reason,
    @Schema(description = "Operator ID", example = "501")
    Long operatorId
) {
}
