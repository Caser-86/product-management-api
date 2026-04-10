package com.example.ecommerce.product.application;

import com.example.ecommerce.product.api.ProductCreateRequest;
import com.example.ecommerce.product.api.ProductResponse;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCommandService {

    private final ProductSpuRepository spuRepository;

    public ProductCommandService(ProductSpuRepository spuRepository) {
        this.spuRepository = spuRepository;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        ProductSpuEntity spu = ProductSpuEntity.draft(
            request.merchantId(),
            "SPU-" + request.title().hashCode(),
            request.title(),
            request.categoryId()
        );
        request.skus().forEach(sku ->
            spu.addSku(ProductSkuEntity.of(request.merchantId(), sku.skuCode(), sku.specSnapshot(), sku.specHash()))
        );
        ProductSpuEntity saved = spuRepository.save(spu);
        return new ProductResponse(saved.getId(), saved.getTitle(), saved.getMerchantId(), saved.getCategoryId());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new IllegalArgumentException("product not found"));
        return new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId());
    }
}
