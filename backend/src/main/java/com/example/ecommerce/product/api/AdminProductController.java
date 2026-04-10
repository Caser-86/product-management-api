package com.example.ecommerce.product.api;

import com.example.ecommerce.product.application.ProductCommandService;
import com.example.ecommerce.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Products", description = "Admin-side product management endpoints")
public class AdminProductController {

    private final ProductCommandService productCommandService;

    public AdminProductController(ProductCommandService productCommandService) {
        this.productCommandService = productCommandService;
    }

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a product with one or more SKUs and initializes inventory.")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(productCommandService.create(request)));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update product", description = "Updates basic product information such as title and category.")
    public ApiResponse<ProductResponse> update(
        @Parameter(description = "Product ID", example = "1001")
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.success(productCommandService.update(productId, request));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete product", description = "Soft deletes a product so it no longer appears in admin queries.")
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productCommandService.delete(productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product detail", description = "Returns basic detail for a single product.")
    public ApiResponse<ProductResponse> get(@PathVariable Long productId) {
        return ApiResponse.success(productCommandService.get(productId));
    }

    @GetMapping
    @Operation(summary = "List products", description = "Lists products for a merchant with pagination.")
    public ApiResponse<ProductListResponse> list(
        @Parameter(description = "Merchant ID", example = "2001")
        @RequestParam(required = false) Long merchantId,
        @Parameter(description = "Page number, starting from 1", example = "1")
        @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "Page size", example = "20")
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.success(productCommandService.list(merchantId, page, pageSize));
    }

    @PostMapping("/{productId}/submit-for-review")
    @Operation(summary = "Submit product for review", description = "Submits a draft product for platform review.")
    public ApiResponse<ProductResponse> submitForReview(
        @PathVariable Long productId,
        @Valid @RequestBody(required = false) ProductWorkflowActionRequest request
    ) {
        return ApiResponse.success(productCommandService.submitForReview(productId, request));
    }

    @PostMapping("/{productId}/resubmit-for-review")
    @Operation(summary = "Resubmit product for review", description = "Resubmits a rejected product for platform review.")
    public ApiResponse<ProductResponse> resubmitForReview(
        @PathVariable Long productId,
        @Valid @RequestBody(required = false) ProductWorkflowActionRequest request
    ) {
        return ApiResponse.success(productCommandService.resubmitForReview(productId, request));
    }

    @PostMapping("/{productId}/approve")
    @Operation(summary = "Approve product", description = "Platform admin approves a submitted product.")
    public ApiResponse<ProductResponse> approve(
        @PathVariable Long productId,
        @Valid @RequestBody(required = false) ProductWorkflowActionRequest request
    ) {
        return ApiResponse.success(productCommandService.approve(productId, request));
    }

    @PostMapping("/{productId}/reject")
    @Operation(summary = "Reject product", description = "Platform admin rejects a submitted product.")
    public ApiResponse<ProductResponse> reject(
        @PathVariable Long productId,
        @Valid @RequestBody ProductWorkflowRejectRequest request
    ) {
        return ApiResponse.success(productCommandService.reject(productId, request));
    }

    @PostMapping("/{productId}/publish")
    @Operation(summary = "Publish product", description = "Platform admin publishes an approved product.")
    public ApiResponse<ProductResponse> publish(
        @PathVariable Long productId,
        @Valid @RequestBody(required = false) ProductWorkflowActionRequest request
    ) {
        return ApiResponse.success(productCommandService.publish(productId, request));
    }

    @PostMapping("/{productId}/unpublish")
    @Operation(summary = "Unpublish product", description = "Platform admin unpublishes a published product.")
    public ApiResponse<ProductResponse> unpublish(
        @PathVariable Long productId,
        @Valid @RequestBody(required = false) ProductWorkflowActionRequest request
    ) {
        return ApiResponse.success(productCommandService.unpublish(productId, request));
    }
}
