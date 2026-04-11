package com.example.ecommerce.search.api;

import com.example.ecommerce.search.application.StorefrontSearchAdminService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/search/storefront")
@Tag(name = "Admin Storefront Search", description = "Admin-side storefront projection maintenance endpoints")
public class StorefrontSearchAdminController {

    private final StorefrontSearchAdminService storefrontSearchAdminService;

    public StorefrontSearchAdminController(StorefrontSearchAdminService storefrontSearchAdminService) {
        this.storefrontSearchAdminService = storefrontSearchAdminService;
    }

    @PostMapping("/products/{productId}/refresh")
    @Operation(summary = "Refresh storefront projection row", description = "Rebuilds a single product row in the storefront projection.")
    public ApiResponse<StorefrontProjectionRefreshResponse> refresh(@PathVariable Long productId) {
        return ApiResponse.success(storefrontSearchAdminService.refreshProduct(productId));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "Rebuild storefront projection", description = "Synchronously rebuilds storefront projection rows for all products.")
    public ApiResponse<StorefrontProjectionRebuildResponse> rebuild() {
        return ApiResponse.success(storefrontSearchAdminService.rebuildAll());
    }
}
