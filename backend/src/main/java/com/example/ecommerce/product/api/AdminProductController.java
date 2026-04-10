package com.example.ecommerce.product.api;

import com.example.ecommerce.product.application.ProductCommandService;
import com.example.ecommerce.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductCommandService productCommandService;

    public AdminProductController(ProductCommandService productCommandService) {
        this.productCommandService = productCommandService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(productCommandService.create(request)));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> update(
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.success(productCommandService.update(productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productCommandService.delete(productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.success(productCommandService.get(productId));
    }

    @GetMapping
    public ApiResponse<ProductListResponse> list(
        @RequestParam Long merchantId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(productCommandService.list(merchantId, page, pageSize));
    }
}
