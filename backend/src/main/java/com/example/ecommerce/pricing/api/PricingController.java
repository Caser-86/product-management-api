package com.example.ecommerce.pricing.api;

import com.example.ecommerce.pricing.application.PricingService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Pricing", description = "Price update, scheduling, and history endpoints")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PatchMapping("/admin/skus/{skuId}/prices")
    @Operation(summary = "Update price", description = "Updates the current effective price for a SKU and records history.")
    public ApiResponse<Void> update(@PathVariable Long skuId, @Valid @RequestBody PriceUpdateRequest request) {
        pricingService.updatePrice(skuId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/admin/skus/{skuId}/price-schedules")
    @Operation(summary = "Create price schedule", description = "Creates a scheduled future price change for a SKU.")
    public ApiResponse<Map<String, Object>> createSchedule(
        @Parameter(description = "SKU ID", example = "20001")
        @PathVariable Long skuId,
        @Valid @RequestBody PriceScheduleRequest request
    ) {
        return ApiResponse.success(pricingService.createSchedule(skuId, request));
    }

    @PostMapping("/admin/price-schedules/{scheduleId}/apply")
    @Operation(summary = "Apply price schedule", description = "Applies a due price schedule immediately.")
    public ApiResponse<Void> applySchedule(@PathVariable Long scheduleId) {
        pricingService.applyScheduledPrice(scheduleId);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/skus/{skuId}/price-history")
    @Operation(summary = "Get price history", description = "Returns price change history for a SKU.")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long skuId) {
        return ApiResponse.success(pricingService.history(skuId));
    }
}
