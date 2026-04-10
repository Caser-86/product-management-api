package com.example.ecommerce.product.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.product.api.ProductCreateRequest;
import com.example.ecommerce.product.api.ProductListResponse;
import com.example.ecommerce.product.api.ProductResponse;
import com.example.ecommerce.product.api.ProductUpdateRequest;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.shared.auth.AuthContext;
import com.example.ecommerce.shared.auth.AuthContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        Long merchantId = effectiveMerchantId(request.merchantId(), true);
        ProductSpuEntity spu = ProductSpuEntity.draft(
            merchantId,
            "SPU-" + UUID.randomUUID().toString().substring(0, 8),
            request.title(),
            request.categoryId()
        );
        request.skus().forEach(sku ->
            spu.addSku(ProductSkuEntity.of(merchantId, sku.skuCode(), sku.specSnapshot(), sku.specHash()))
        );
        ProductSpuEntity saved = spuRepository.save(spu);
        Map<String, Integer> initialStockBySkuCode = request.skus().stream()
            .collect(Collectors.toMap(ProductCreateRequest.SkuInput::skuCode, ProductCreateRequest.SkuInput::initialStock));
        for (ProductSkuEntity sku : saved.getSkus()) {
            Integer initialStock = initialStockBySkuCode.get(sku.getSkuCode());
            if (initialStock == null) {
                throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "initial stock missing for sku");
            }
            inventoryBalanceRepository.save(InventoryBalanceEntity.initial(sku.getId(), sku.getMerchantId(), initialStock));
        }
        return new ProductResponse(saved.getId(), saved.getTitle(), saved.getMerchantId(), saved.getCategoryId());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
        assertMerchantScope(spu.getMerchantId());
        return new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId());
    }

    @Transactional
    public ProductResponse update(Long productId, ProductUpdateRequest request) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
        assertMerchantScope(spu.getMerchantId());
        spu.updateBasics(request.title(), request.categoryId());
        return new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId());
    }

    @Transactional
    public void delete(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        assertMerchantScope(spu.getMerchantId());
        spu.archive();
    }

    @Transactional(readOnly = true)
    public ProductListResponse list(Long merchantId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Long effectiveMerchantId = effectiveMerchantId(merchantId, false);
        var pageable = org.springframework.data.domain.PageRequest.of(safePage - 1, safePageSize);
        var pageResult = effectiveMerchantId == null
            ? spuRepository.findByStatusNot("deleted", pageable)
            : spuRepository.findByMerchantIdAndStatusNot(effectiveMerchantId, "deleted", pageable);
        var items = pageResult.getContent().stream()
            .map(spu -> new ProductResponse(spu.getId(), spu.getTitle(), spu.getMerchantId(), spu.getCategoryId()))
            .toList();
        return new ProductListResponse(items, safePage, safePageSize, pageResult.getTotalElements());
    }

    private Long effectiveMerchantId(Long requestedMerchantId, boolean required) {
        AuthContext auth = AuthContextHolder.getRequired();
        if (!auth.isPlatformAdmin()) {
            return auth.merchantId();
        }
        if (required && requestedMerchantId == null) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "merchantId is required");
        }
        return requestedMerchantId;
    }

    private void assertMerchantScope(Long resourceMerchantId) {
        AuthContext auth = AuthContextHolder.getRequired();
        if (!auth.isPlatformAdmin() && !auth.merchantId().equals(resourceMerchantId)) {
            throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
        }
    }
}
