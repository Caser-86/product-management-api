package com.example.ecommerce.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Inventory reservation action response")
public record InventoryReservationResponse(
    @Schema(description = "Reservation identifier", example = "order-8001-attempt-1")
    String reservationId,
    @Schema(description = "Reservation workflow status", example = "reserved")
    String status
) {
}
