package com.example.ecommerce.search.api;

import com.example.ecommerce.search.application.StorefrontSearchService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@Tag(name = "Storefront Search", description = "Storefront-facing product search endpoints")
public class StorefrontProductController {

    private final StorefrontSearchService storefrontSearchService;

    public StorefrontProductController(StorefrontSearchService storefrontSearchService) {
        this.storefrontSearchService = storefrontSearchService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search products", description = "Searches storefront products by keyword, category, price range, sorting, and pagination.")
    public ApiResponse<StorefrontSearchResponse> search(
        @Parameter(description = "Keyword to match in the product title", example = "hoodie")
        @RequestParam(required = false) String keyword,
        @Parameter(description = "Category ID filter", example = "33")
        @RequestParam(required = false) Long categoryId,
        @Parameter(description = "Minimum shopper price filter", example = "100")
        @RequestParam(required = false) BigDecimal minPrice,
        @Parameter(description = "Maximum shopper price filter", example = "300")
        @RequestParam(required = false) BigDecimal maxPrice,
        @Parameter(description = "Sort order: newest, price_asc, or price_desc", example = "price_asc")
        @RequestParam(required = false) String sort,
        @Parameter(description = "When true, only return products with available stock", example = "true")
        @RequestParam(required = false) Boolean inStockOnly,
        @Parameter(description = "Page number, starting from 1", example = "1")
        @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Page size", example = "20")
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(storefrontSearchService.search(keyword, categoryId, minPrice, maxPrice, inStockOnly, sort, page, pageSize));
    }
}
