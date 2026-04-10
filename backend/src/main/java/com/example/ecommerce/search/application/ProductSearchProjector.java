package com.example.ecommerce.search.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.search.domain.StorefrontProductSearchEntity;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.shared.outbox.OutboxEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductSearchProjector {

    private final ProductSpuRepository productSpuRepository;
    private final PriceCurrentRepository priceCurrentRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StorefrontProductSearchRepository storefrontProductSearchRepository;

    public ProductSearchProjector(
        ProductSpuRepository productSpuRepository,
        PriceCurrentRepository priceCurrentRepository,
        InventoryBalanceRepository inventoryBalanceRepository,
        StorefrontProductSearchRepository storefrontProductSearchRepository
    ) {
        this.productSpuRepository = productSpuRepository;
        this.priceCurrentRepository = priceCurrentRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.storefrontProductSearchRepository = storefrontProductSearchRepository;
    }

    @Transactional
    public void refresh(Long productId) {
        var spu = productSpuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.getSkus().isEmpty()) {
            storefrontProductSearchRepository.deleteById(productId);
            return;
        }
        var sku = spu.getSkus().get(0);
        var price = priceCurrentRepository.findById(sku.getId()).orElse(null);
        var inventory = inventoryBalanceRepository.findById(sku.getId()).orElse(null);
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.from(spu, sku, price, inventory));
    }

    @Transactional
    public void project(OutboxEvent event) {
        if (!"ProductChanged".equals(event.eventType())) {
            return;
        }
        refresh(Long.valueOf(event.aggregateId()));
    }
}
