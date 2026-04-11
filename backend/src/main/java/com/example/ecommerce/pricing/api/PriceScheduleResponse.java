package com.example.ecommerce.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Price schedule creation response")
public record PriceScheduleResponse(
    @Schema(description = "Created schedule ID", example = "1001")
    Long scheduleId,
    @Schema(description = "Current schedule status", example = "pending")
    String status
) {
}
