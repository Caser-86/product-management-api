package com.example.ecommerce.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceScheduleRequest(
    @Schema(description = "Future list price", example = "299.00")
    @NotNull(message = "listPrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "listPrice must be positive")
    BigDecimal listPrice,
    @Schema(description = "Future sale price", example = "239.00")
    @NotNull(message = "salePrice is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "salePrice must be positive")
    BigDecimal salePrice,
    @Schema(description = "When the scheduled price becomes effective", example = "2026-04-11T09:00:00")
    @NotNull(message = "effectiveAt is required")
    LocalDateTime effectiveAt,
    @Schema(description = "Optional expiration time for the schedule", example = "2026-04-12T09:00:00")
    LocalDateTime expireAt,
    @Schema(description = "Reason for the scheduled change", example = "scheduled release")
    String reason,
    @Schema(description = "Operator ID", example = "7001")
    Long operatorId
) {
}
