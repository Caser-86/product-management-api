package com.example.ecommerce.search.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.search.api.StorefrontSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StorefrontSearchService {

    private final ProductSpuRepository productSpuRepository;
    private final PriceCurrentRepository priceCurrentRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;

    public StorefrontSearchService(
        ProductSpuRepository productSpuRepository,
        PriceCurrentRepository priceCurrentRepository,
        InventoryBalanceRepository inventoryBalanceRepository
    ) {
        this.productSpuRepository = productSpuRepository;
        this.priceCurrentRepository = priceCurrentRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
    }

    @Transactional(readOnly = true)
    public StorefrontSearchResponse search(String keyword, Long categoryId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        PageRequest pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<ProductSpuEntity> pageResult = query(keyword, categoryId, pageable);

        List<StorefrontSearchResponse.Item> items = pageResult.getContent().stream()
            .map(spu -> productSpuRepository.findWithSkusById(spu.getId()).orElse(spu))
            .filter(spu -> !spu.getSkus().isEmpty())
            .map(spu -> {
                var sku = spu.getSkus().get(0);
                var price = priceCurrentRepository.findById(sku.getId()).orElse(null);
                var inventory = inventoryBalanceRepository.findById(sku.getId()).orElse(null);
                double minPrice = price == null ? 0.0 : price.getSalePrice().doubleValue();
                double maxPrice = price == null ? 0.0 : price.getListPrice().doubleValue();
                String stockStatus = inventory != null && inventory.getAvailableQty() > 0 ? "in_stock" : "out_of_stock";
                return new StorefrontSearchResponse.Item(spu.getId(), spu.getTitle(), minPrice, maxPrice, stockStatus);
            })
            .toList();

        return new StorefrontSearchResponse(items, safePage, safePageSize, pageResult.getTotalElements());
    }

    private Page<ProductSpuEntity> query(String keyword, Long categoryId, PageRequest pageable) {
        String effectiveKeyword = keyword == null ? "" : keyword.trim();
        boolean hasKeyword = !effectiveKeyword.isBlank();
        boolean hasCategory = categoryId != null;

        if (hasKeyword && hasCategory) {
            return productSpuRepository.findByStatusNotAndTitleContainingIgnoreCaseAndCategoryId("deleted", effectiveKeyword, categoryId, pageable);
        }
        if (hasKeyword) {
            return productSpuRepository.findByStatusNotAndTitleContainingIgnoreCase("deleted", effectiveKeyword, pageable);
        }
        if (hasCategory) {
            return productSpuRepository.findByStatusNotAndCategoryId("deleted", categoryId, pageable);
        }
        return productSpuRepository.findByStatusNot("deleted", pageable);
    }
}
