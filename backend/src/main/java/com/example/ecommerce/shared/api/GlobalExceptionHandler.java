package com.example.ecommerce.shared.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                ErrorCode.COMMON_VALIDATION_FAILED.name(),
                ex.getMessage() == null ? "validation failed" : ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null ? "validation failed" : error.getDefaultMessage())
            .orElse("validation failed");
        return ResponseEntity.badRequest()
            .body(new ApiResponse<>(false, ErrorCode.COMMON_VALIDATION_FAILED.name(), message, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getMessage() == null ? "validation failed" : violation.getMessage())
            .orElse("validation failed");
        return ResponseEntity.badRequest()
            .body(new ApiResponse<>(false, ErrorCode.COMMON_VALIDATION_FAILED.name(), message, null));
    }
}
