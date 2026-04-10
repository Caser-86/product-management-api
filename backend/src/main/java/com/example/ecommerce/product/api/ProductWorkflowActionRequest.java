package com.example.ecommerce.product.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ProductWorkflowActionRequest(
    @Schema(description = "Optional workflow comment", example = "ready for review")
    @Size(max = 500, message = "comment length must be <= 500")
    String comment
) {
}
