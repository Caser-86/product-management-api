package com.example.ecommerce.search.domain;

import com.example.ecommerce.search.application.StorefrontSearchSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface StorefrontProductSearchCustomRepository {

    Page<StorefrontProductSearchEntity> searchVisibleProducts(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        StorefrontSearchSort sort,
        Pageable pageable
    );
}
