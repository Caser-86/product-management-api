package com.example.ecommerce.search.api;

import com.example.ecommerce.search.application.StorefrontSearchService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Storefront Search", description = "Storefront-facing product search endpoints")
public class StorefrontProductController {

    private final StorefrontSearchService storefrontSearchService;

    public StorefrontProductController(StorefrontSearchService storefrontSearchService) {
        this.storefrontSearchService = storefrontSearchService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search products", description = "Searches storefront products by keyword, category, and pagination.")
    public ApiResponse<StorefrontSearchResponse> search(
        @Parameter(description = "Keyword to match in the product title", example = "hoodie")
        @RequestParam(required = false) String keyword,
        @Parameter(description = "Category ID filter", example = "33")
        @RequestParam(required = false) Long categoryId,
        @Parameter(description = "Page number, starting from 1", example = "1")
        @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Page size", example = "20")
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(storefrontSearchService.search(keyword, categoryId, page, pageSize));
    }
}
