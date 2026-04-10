package com.example.ecommerce.inventory.api;

import com.example.ecommerce.inventory.application.InventoryService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Inventory", description = "Inventory reservation, confirmation, adjustment, and snapshot endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/inventory/reservations")
    @Operation(summary = "Reserve inventory", description = "Creates an inventory reservation for an order attempt.")
    public ApiResponse<Map<String, Object>> reserve(@RequestBody InventoryReservationRequest request) {
        String reservationId = inventoryService.reserve(request.idempotencyKey(), request.bizId(), request.items());
        return ApiResponse.success(Map.of("reservationId", reservationId, "status", "reserved"));
    }

    @PostMapping("/inventory/reservations/{reservationId}/confirm")
    @Operation(summary = "Confirm reservation", description = "Confirms a reservation and moves reserved quantity into sold quantity.")
    public ApiResponse<Void> confirm(
        @Parameter(description = "Reservation ID", example = "order-8001-attempt-1")
        @PathVariable String reservationId,
        @Valid @RequestBody InventoryReservationConfirmRequest request
    ) {
        inventoryService.confirm(reservationId, request.bizId());
        return ApiResponse.success(null);
    }

    @PostMapping("/admin/skus/{skuId}/inventory/adjustments")
    @Operation(summary = "Adjust inventory", description = "Applies a manual stock adjustment to a SKU.")
    public ApiResponse<Map<String, Object>> adjust(
        @Parameter(description = "SKU ID", example = "20001")
        @PathVariable Long skuId,
        @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return ApiResponse.success(inventoryService.adjust(skuId, request.delta(), request.reason(), request.operatorId()));
    }

    @GetMapping("/admin/skus/{skuId}/inventory")
    @Operation(summary = "Get inventory snapshot", description = "Returns current inventory counters for a SKU.")
    public ApiResponse<Map<String, Object>> inventory(@PathVariable Long skuId) {
        return ApiResponse.success(inventoryService.snapshot(skuId));
    }

    @GetMapping("/admin/skus/{skuId}/inventory/history")
    @Operation(summary = "Get inventory history", description = "Returns immutable inventory ledger records for a SKU.")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long skuId) {
        return ApiResponse.success(inventoryService.history(skuId));
    }
}
