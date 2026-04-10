package com.example.ecommerce.shared.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void maps_bind_exception_to_common_validation_failed() {
        BindException ex = new BindException(new Object(), "request");
        ex.addError(new FieldError("request", "title", "title is required"));

        var response = handler.handleBindException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("title is required");
    }

    @Test
    void maps_constraint_violation_exception_to_common_validation_failed() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("merchantId is required");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        var response = handler.handleConstraintViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("merchantId is required");
    }
}
