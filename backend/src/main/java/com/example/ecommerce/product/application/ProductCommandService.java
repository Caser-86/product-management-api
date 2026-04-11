package com.example.ecommerce.product.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.product.api.ProductCreateRequest;
import com.example.ecommerce.product.api.ProductListResponse;
import com.example.ecommerce.product.api.ProductResponse;
import com.example.ecommerce.product.api.ProductUpdateRequest;
import com.example.ecommerce.product.api.ProductWorkflowActionRequest;
import com.example.ecommerce.product.api.ProductWorkflowRejectRequest;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryEntity;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.example.ecommerce.search.application.ProductSearchProjector;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.shared.auth.AuthContext;
import com.example.ecommerce.shared.auth.AuthContextHolder;
import com.example.ecommerce.shared.auth.AuthRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductCommandService {

    private final ProductSpuRepository spuRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final ProductWorkflowHistoryRepository productWorkflowHistoryRepository;
    private final ProductSearchProjector productSearchProjector;

    public ProductCommandService(
        ProductSpuRepository spuRepository,
        InventoryBalanceRepository inventoryBalanceRepository,
        ProductWorkflowHistoryRepository productWorkflowHistoryRepository,
        ProductSearchProjector productSearchProjector
    ) {
        this.spuRepository = spuRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.productWorkflowHistoryRepository = productWorkflowHistoryRepository;
        this.productSearchProjector = productSearchProjector;
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
        productSearchProjector.refresh(saved.getId());
        return toProductResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
        assertMerchantScope(spu.getMerchantId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse update(Long productId, ProductUpdateRequest request) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
        assertMerchantScope(spu.getMerchantId());
        boolean requiresReviewReset = "approved".equals(spu.getAuditStatus());
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        spu.updateBasics(request.title(), request.categoryId());
        if (requiresReviewReset) {
            spu.resetToDraftAfterMutation();
            recordWorkflow(
                spu,
                "update_reset",
                fromStatus,
                fromAuditStatus,
                fromPublishStatus,
                "product updated after approval"
            );
        }
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public void delete(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        assertMerchantScope(spu.getMerchantId());
        spu.archive();
        productSearchProjector.refresh(spu.getId());
    }

    @Transactional(readOnly = true)
    public ProductListResponse list(
        Long merchantId,
        String status,
        String auditStatus,
        String publishStatus,
        String keyword,
        String sort,
        int page,
        int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Long effectiveMerchantId = effectiveMerchantId(merchantId, false);
        validateListFilters(status, auditStatus, publishStatus);
        AdminProductListSort adminProductListSort = AdminProductListSort.parse(sort);
        var pageable = PageRequest.of(safePage - 1, safePageSize);
        var pageResult = spuRepository.searchAdminProducts(
            effectiveMerchantId,
            normalize(status),
            normalize(auditStatus),
            normalize(publishStatus),
            normalize(keyword),
            adminProductListSort,
            pageable
        );
        var items = pageResult.getContent().stream()
            .map(this::toProductResponse)
            .toList();
        return new ProductListResponse(items, safePage, safePageSize, pageResult.getTotalElements());
    }

    @Transactional
    public ProductResponse submitForReview(Long productId, ProductWorkflowActionRequest request) {
        AuthContext auth = assertMerchantAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        assertMerchantOwnedOrNotFound(spu, auth);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        spu.submitForReview(LocalDateTime.now());
        recordWorkflow(spu, "submit_for_review", fromStatus, fromAuditStatus, fromPublishStatus, commentOf(request));
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse resubmitForReview(Long productId, ProductWorkflowActionRequest request) {
        AuthContext auth = assertMerchantAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        assertMerchantOwnedOrNotFound(spu, auth);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        spu.resubmitForReview(LocalDateTime.now());
        recordWorkflow(spu, "resubmit_for_review", fromStatus, fromAuditStatus, fromPublishStatus, commentOf(request));
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse approve(Long productId, ProductWorkflowActionRequest request) {
        assertPlatformAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        AuthContext auth = AuthContextHolder.getRequired();
        String comment = commentOf(request);
        spu.approve(auth.userId(), comment, LocalDateTime.now());
        recordWorkflow(spu, "approve", fromStatus, fromAuditStatus, fromPublishStatus, comment);
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse reject(Long productId, ProductWorkflowRejectRequest request) {
        assertPlatformAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        AuthContext auth = AuthContextHolder.getRequired();
        spu.reject(auth.userId(), request.reason(), LocalDateTime.now());
        recordWorkflow(spu, "reject", fromStatus, fromAuditStatus, fromPublishStatus, request.reason());
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse publish(Long productId, ProductWorkflowActionRequest request) {
        assertPlatformAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        AuthContext auth = AuthContextHolder.getRequired();
        spu.publish(auth.userId(), LocalDateTime.now());
        recordWorkflow(spu, "publish", fromStatus, fromAuditStatus, fromPublishStatus, commentOf(request));
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    @Transactional
    public ProductResponse unpublish(Long productId, ProductWorkflowActionRequest request) {
        assertPlatformAdmin();
        ProductSpuEntity spu = loadProductForWorkflow(productId);
        String fromStatus = spu.getStatus();
        String fromAuditStatus = spu.getAuditStatus();
        String fromPublishStatus = spu.getPublishStatus();
        spu.unpublish();
        recordWorkflow(spu, "unpublish", fromStatus, fromAuditStatus, fromPublishStatus, commentOf(request));
        productSearchProjector.refresh(spu.getId());
        return toProductResponse(spu);
    }

    private ProductResponse toProductResponse(ProductSpuEntity spu) {
        return new ProductResponse(
            spu.getId(),
            spu.getTitle(),
            spu.getMerchantId(),
            spu.getCategoryId(),
            spu.getStatus(),
            spu.getAuditStatus(),
            spu.getPublishStatus(),
            spu.getAuditComment(),
            spu.getSubmittedAt(),
            spu.getAuditAt(),
            spu.getPublishedAt()
        );
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

    private ProductSpuEntity loadProductForWorkflow(Long productId) {
        ProductSpuEntity spu = spuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
        return spu;
    }

    private void assertPlatformAdmin() {
        AuthContext auth = AuthContextHolder.getRequired();
        if (!auth.isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
        }
    }

    private AuthContext assertMerchantAdmin() {
        AuthContext auth = AuthContextHolder.getRequired();
        if (auth.role() != AuthRole.MERCHANT_ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
        }
        return auth;
    }

    private void assertMerchantOwnedOrNotFound(ProductSpuEntity spu, AuthContext auth) {
        if (!spu.getMerchantId().equals(auth.merchantId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
        }
    }

    private void recordWorkflow(
        ProductSpuEntity spu,
        String action,
        String fromStatus,
        String fromAuditStatus,
        String fromPublishStatus,
        String comment
    ) {
        AuthContext auth = AuthContextHolder.getRequired();
        productWorkflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
            spu.getId(),
            action,
            fromStatus,
            spu.getStatus(),
            fromAuditStatus,
            spu.getAuditStatus(),
            fromPublishStatus,
            spu.getPublishStatus(),
            auth.userId(),
            auth.role().name(),
            comment
        ));
    }

    private String commentOf(ProductWorkflowActionRequest request) {
        if (request == null) {
            return null;
        }
        return request.comment();
    }

    private void validateListFilters(String status, String auditStatus, String publishStatus) {
        if (status != null && !status.isBlank() && !"draft".equals(status) && !"active".equals(status)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "unsupported product status");
        }
        if (auditStatus != null && !auditStatus.isBlank()
            && !"pending".equals(auditStatus)
            && !"approved".equals(auditStatus)
            && !"rejected".equals(auditStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "unsupported audit status");
        }
        if (publishStatus != null && !publishStatus.isBlank()
            && !"published".equals(publishStatus)
            && !"unpublished".equals(publishStatus)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "unsupported publish status");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
