package com.example.ecommerce.search.application;

import com.example.ecommerce.search.api.StorefrontSearchResponse;
import com.example.ecommerce.search.domain.StorefrontProductSearchEntity;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class StorefrontSearchService {

    private final StorefrontProductSearchRepository storefrontProductSearchRepository;

    public StorefrontSearchService(StorefrontProductSearchRepository storefrontProductSearchRepository) {
        this.storefrontProductSearchRepository = storefrontProductSearchRepository;
    }

    @Transactional(readOnly = true)
    public StorefrontSearchResponse search(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String sort,
        int page,
        int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        validatePriceRange(minPrice, maxPrice);
        StorefrontSearchSort storefrontSearchSort = StorefrontSearchSort.parse(sort);
        PageRequest pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<StorefrontProductSearchEntity> pageResult = storefrontProductSearchRepository.searchVisibleProducts(
            keyword,
            categoryId,
            minPrice,
            maxPrice,
            storefrontSearchSort,
            pageable
        );

        List<StorefrontSearchResponse.Item> items = pageResult.getContent().stream()
            .map(row -> new StorefrontSearchResponse.Item(
                row.getProductId(),
                row.getTitle(),
                row.getMinPrice().doubleValue(),
                row.getMaxPrice().doubleValue(),
                row.getStockStatus()
            ))
            .toList();

        return new StorefrontSearchResponse(items, safePage, safePageSize, pageResult.getTotalElements());
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "minPrice must be non-negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "maxPrice must be non-negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "minPrice cannot exceed maxPrice");
        }
    }
}
