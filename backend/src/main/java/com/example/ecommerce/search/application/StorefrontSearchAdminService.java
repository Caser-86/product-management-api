package com.example.ecommerce.search.application;

import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.search.api.StorefrontProjectionRebuildResponse;
import com.example.ecommerce.search.api.StorefrontProjectionRefreshResponse;
import com.example.ecommerce.shared.auth.AuthContext;
import com.example.ecommerce.shared.auth.AuthContextHolder;
import com.example.ecommerce.shared.auth.AuthRole;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StorefrontSearchAdminService {

    private static final int REBUILD_PAGE_SIZE = 100;

    private final ProductSpuRepository productSpuRepository;
    private final ProductSearchProjector productSearchProjector;

    public StorefrontSearchAdminService(
        ProductSpuRepository productSpuRepository,
        ProductSearchProjector productSearchProjector
    ) {
        this.productSpuRepository = productSpuRepository;
        this.productSearchProjector = productSearchProjector;
    }

    @Transactional
    public StorefrontProjectionRefreshResponse refreshProduct(Long productId) {
        requirePlatformAdmin();
        productSearchProjector.refresh(productId);
        return new StorefrontProjectionRefreshResponse(productId, "refreshed");
    }

    public StorefrontProjectionRebuildResponse rebuildAll() {
        requirePlatformAdmin();
        long startedAt = System.currentTimeMillis();
        int processedCount = 0;
        int successCount = 0;
        List<StorefrontProjectionRebuildResponse.Failure> failures = new ArrayList<>();
        Pageable pageRequest = PageRequest.of(0, REBUILD_PAGE_SIZE);
        Page<Long> page;

        do {
            page = productSpuRepository.findIdsForProjectionRebuild(pageRequest);
            for (Long productId : page.getContent()) {
                processedCount++;
                try {
                    productSearchProjector.refresh(productId);
                    successCount++;
                } catch (BusinessException ex) {
                    failures.add(new StorefrontProjectionRebuildResponse.Failure(
                        productId,
                        ex.getErrorCode().name(),
                        ex.getMessage()
                    ));
                } catch (Exception ex) {
                    failures.add(new StorefrontProjectionRebuildResponse.Failure(
                        productId,
                        ErrorCode.COMMON_INTERNAL_ERROR.name(),
                        ex.getMessage() == null ? "internal error" : ex.getMessage()
                    ));
                }
            }
            if (page.hasNext()) {
                pageRequest = page.nextPageable();
            }
        } while (page.hasNext());

        return new StorefrontProjectionRebuildResponse(
            processedCount,
            successCount,
            failures.size(),
            System.currentTimeMillis() - startedAt,
            failures
        );
    }

    private void requirePlatformAdmin() {
        AuthContext authContext = AuthContextHolder.getRequired();
        if (authContext.role() != AuthRole.PLATFORM_ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "platform admin role required");
        }
    }
}
