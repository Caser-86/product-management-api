package com.example.ecommerce.pricing.api;

import com.example.ecommerce.pricing.application.PricingService;
import com.example.ecommerce.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PatchMapping("/admin/skus/{skuId}/prices")
    public ApiResponse<Void> update(@PathVariable Long skuId, @Valid @RequestBody PriceUpdateRequest request) {
        pricingService.updatePrice(skuId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin/skus/{skuId}/price-history")
    public ApiResponse<Map<String, Object>> history(@PathVariable Long skuId) {
        return ApiResponse.success(pricingService.history(skuId));
    }
}
