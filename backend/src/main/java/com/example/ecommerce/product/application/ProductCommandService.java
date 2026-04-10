package com.example.ecommerce.product.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.product.api.ProductCreateRequest;
import com.example.ecommerce.product.api.ProductListResponse;
import com.example.ecommerce.product.api.ProductResponse;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductCommandService {

    private final ProductSpuRepository spuRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;

    public ProductCommandService(ProductSpuRepository spuRepository, InventoryBalanceRepository inventoryBalanceRepository) {
        this.spuRepository = spuRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        ProductValidation.validateUniqueSpecHashes(request);
        ProductSpuEntity spu = ProductSpuEntity.draft(
            request.merchantId(),
            "SPU-" + UUID.randomUUID().toString().substring(0, 8),
            request.title(),
            request.categoryId()
        );
        request.skus().forEach(sku ->
            spu.addSku(ProductSkuEntity.of(request.merchantId(), sku.skuCode(), sku.specSnapshot(), sku.specHash()))
        );
        ProductSpuEntity saved = spuRepository.save(spu);
        for (int i = 0; i < saved.getSkus().size(); i++) {
            ProductSkuEntity sku = saved.getSkus().get(i);
            int initialStock = request.skus().get(i).initialStock();
            inventoryBalanceRepository.save(InventoryBalanceEntity.initial(sku.getId(), sku.getMerchantId(), initialStock));
        }
        return new ProductResponse(saved.getId(), saved.getTitle(), saved.getMerchantId(), saved.getCategoryId());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new IllegalArgumentException("product not found"));
        return new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId());
    }

    @Transactional(readOnly = true)
    public ProductListResponse list(Long merchantId, int page, int pageSize) {
        var pageResult = spuRepository.findAll(org.springframework.data.domain.PageRequest.of(page - 1, pageSize));
        var items = pageResult.getContent().stream()
            .filter(spu -> spu.getMerchantId().equals(merchantId))
            .map(spu -> new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId()))
            .toList();
        return new ProductListResponse(items, page, pageSize, pageResult.getTotalElements());
    }
}
