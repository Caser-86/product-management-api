package com.example.ecommerce.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Paginated SKU price schedule response")
public record PriceScheduleListResponse(
    @Schema(description = "Schedule items for the current page")
    List<Item> items,
    @Schema(description = "Current page number starting from 1", example = "1")
    int page,
    @Schema(description = "Current page size", example = "20")
    int pageSize,
    @Schema(description = "Total number of schedules", example = "2")
    long total
) {
    @Schema(description = "Price schedule list item")
    public record Item(
        @Schema(description = "Price schedule ID", example = "1001")
        Long scheduleId,
        @Schema(description = "Schedule status", example = "pending")
        String status,
        @Schema(description = "Effective time", example = "2026-04-12T09:00:00")
        LocalDateTime effectiveAt,
        @Schema(description = "Optional expiration time", example = "2026-04-13T09:00:00")
        LocalDateTime expireAt,
        @Schema(description = "Target price snapshot")
        PriceSnapshot targetPrice,
        @Schema(description = "Record creation time", example = "2026-04-11T10:30:00")
        LocalDateTime createdAt
    ) {
    }

    @Schema(description = "Typed price snapshot")
    public record PriceSnapshot(
        @Schema(description = "List price", example = "299.00")
        BigDecimal listPrice,
        @Schema(description = "Sale price", example = "239.00")
        BigDecimal salePrice
    ) {
    }
}
