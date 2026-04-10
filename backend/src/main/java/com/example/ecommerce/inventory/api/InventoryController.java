package com.example.ecommerce.inventory.api;

import com.example.ecommerce.inventory.application.InventoryService;
import com.example.ecommerce.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/inventory/reservations")
    public ApiResponse<Map<String, Object>> reserve(@RequestBody InventoryReservationRequest request) {
        var item = request.items().get(0);
        String reservationId = inventoryService.reserve(item.skuId(), item.quantity(), request.bizId());
        return ApiResponse.success(Map.of("reservationId", reservationId, "status", "reserved"));
    }

    @PostMapping("/inventory/reservations/{reservationId}/confirm")
    public ApiResponse<Void> confirm(
        @PathVariable String reservationId,
        @RequestBody InventoryReservationConfirmRequest request
    ) {
        inventoryService.confirm(reservationId);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/skus/{skuId}/inventory")
    public ApiResponse<Map<String, Object>> inventory(@PathVariable Long skuId) {
        return ApiResponse.success(Map.of("skuId", skuId, "soldQty", inventoryService.soldQty(skuId)));
    }
}
