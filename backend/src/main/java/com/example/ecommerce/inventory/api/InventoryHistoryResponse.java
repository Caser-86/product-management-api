package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Paginated inventory ledger history for a SKU")
public record InventoryHistoryResponse(
    @Schema(description = "Inventory ledger entries in reverse chronological order")
    List<Item> items,
    @Schema(description = "Current page number", example = "1")
    int page,
    @Schema(description = "Current page size after clamping", example = "20")
    int pageSize,
    @Schema(description = "Total number of ledger rows", example = "12")
    long total
) {

    @Schema(description = "One immutable inventory ledger entry")
    public record Item(
        @Schema(description = "Business operation type", example = "adjust")
        String bizType,
        @Schema(description = "Business identifier or reason", example = "manual restock")
        String bizId,
        @Schema(description = "Delta applied to available quantity", example = "3")
        int deltaAvailable,
        @Schema(description = "Delta applied to reserved quantity", example = "0")
        int deltaReserved,
        @Schema(description = "Time when the ledger row was created")
        LocalDateTime createdAt
    ) {
    }
}
