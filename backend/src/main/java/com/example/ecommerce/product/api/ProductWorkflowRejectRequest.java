package com.example.ecommerce.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductWorkflowRejectRequest(
    @Schema(description = "Reject reason", example = "missing compliance materials")
    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason length must be <= 500")
    String reason
) {
}
