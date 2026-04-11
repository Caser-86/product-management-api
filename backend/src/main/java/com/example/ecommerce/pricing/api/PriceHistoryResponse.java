package com.example.ecommerce.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Paginated price history for a SKU")
public record PriceHistoryResponse(
    @Schema(description = "History items in reverse chronological order")
    List<Item> items,
    @Schema(description = "Current page number", example = "1")
    int page,
    @Schema(description = "Current page size after clamping", example = "20")
    int pageSize,
    @Schema(description = "Total number of history rows", example = "12")
    long total
) {

    @Schema(description = "One price history change")
    public record Item(
        @Schema(description = "Change source", example = "manual")
        String changeType,
        @Schema(description = "Previous price snapshot. Null when no previous price existed")
        PriceSnapshot oldPrice,
        @Schema(description = "New price snapshot")
        PriceSnapshot newPrice,
        @Schema(description = "Optional reason for the price change", example = "weekend campaign")
        String reason,
        @Schema(description = "Operator user ID", example = "501")
        Long operatorId,
        @Schema(description = "Time when the history row was created")
        LocalDateTime createdAt
    ) {
    }

    @Schema(description = "List and sale price pair")
    public record PriceSnapshot(
        @Schema(description = "List price", example = "199.00")
        BigDecimal listPrice,
        @Schema(description = "Sale price", example = "159.00")
        BigDecimal salePrice
    ) {
    }
}
