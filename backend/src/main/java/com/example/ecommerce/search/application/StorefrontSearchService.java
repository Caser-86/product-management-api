package com.example.ecommerce.search.application;

import com.example.ecommerce.search.api.StorefrontSearchResponse;
import com.example.ecommerce.search.domain.StorefrontProductSearchEntity;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StorefrontSearchService {

    private final StorefrontProductSearchRepository storefrontProductSearchRepository;

    public StorefrontSearchService(StorefrontProductSearchRepository storefrontProductSearchRepository) {
        this.storefrontProductSearchRepository = storefrontProductSearchRepository;
    }

    @Transactional(readOnly = true)
    public StorefrontSearchResponse search(String keyword, Long categoryId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        PageRequest pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<StorefrontProductSearchEntity> pageResult = query(keyword, categoryId, pageable);

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

    private Page<StorefrontProductSearchEntity> query(String keyword, Long categoryId, PageRequest pageable) {
        String effectiveKeyword = keyword == null ? "" : keyword.trim();
        boolean hasKeyword = !effectiveKeyword.isBlank();
        boolean hasCategory = categoryId != null;

        if (hasKeyword && hasCategory) {
            return storefrontProductSearchRepository.findByProductStatusNotAndTitleContainingIgnoreCaseAndCategoryId(
                "deleted",
                effectiveKeyword,
                categoryId,
                pageable
            );
        }
        if (hasKeyword) {
            return storefrontProductSearchRepository.findByProductStatusNotAndTitleContainingIgnoreCase("deleted", effectiveKeyword, pageable);
        }
        if (hasCategory) {
            return storefrontProductSearchRepository.findByProductStatusNotAndCategoryId("deleted", categoryId, pageable);
        }
        return storefrontProductSearchRepository.findByProductStatusNot("deleted", pageable);
    }
}
