package com.example.ecommerce.search.api;

import com.example.ecommerce.search.application.StorefrontSearchService;
import com.example.ecommerce.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorefrontProductController {

    private final StorefrontSearchService storefrontSearchService;

    public StorefrontProductController(StorefrontSearchService storefrontSearchService) {
        this.storefrontSearchService = storefrontSearchService;
    }

    @GetMapping("/products")
    public ApiResponse<StorefrontSearchResponse> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(storefrontSearchService.search(keyword, categoryId, page, pageSize));
    }
}
