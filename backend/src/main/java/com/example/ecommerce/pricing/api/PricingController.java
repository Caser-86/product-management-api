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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<PriceScheduleResponse> createSchedule(
        @Parameter(description = "SKU ID", example = "20001")
        @PathVariable Long skuId,
        @Valid @RequestBody PriceScheduleRequest request
    ) {
        return ApiResponse.success(pricingService.createSchedule(skuId, request));
    }

    @GetMapping("/admin/skus/{skuId}/price-schedules")
    @Operation(summary = "List price schedules", description = "Returns scheduled future price changes for a SKU.")
    public ApiResponse<PriceScheduleListResponse> scheduleList(
        @Parameter(description = "SKU ID", example = "20001")
        @PathVariable Long skuId,
        @Parameter(description = "Page number starting from 1", example = "1")
        @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Page size, maximum 100", example = "20")
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(pricingService.scheduleList(skuId, page, pageSize));
    }

    @PostMapping("/admin/price-schedules/{scheduleId}/apply")
    @Operation(summary = "Apply price schedule", description = "Applies a due price schedule immediately.")
    public ApiResponse<Void> applySchedule(@PathVariable Long scheduleId) {
        pricingService.applyScheduledPrice(scheduleId);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/skus/{skuId}/price-history")
    @Operation(summary = "Get price history", description = "Returns price change history for a SKU.")
    public ApiResponse<PriceHistoryResponse> history(
        @Parameter(description = "SKU ID", example = "20001")
        @PathVariable Long skuId,
        @Parameter(description = "Page number starting from 1", example = "1")
        @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Page size, maximum 100", example = "20")
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(pricingService.history(skuId, page, pageSize));
    }
}
