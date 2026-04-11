package com.example.ecommerce.product.domain;

import com.example.ecommerce.product.application.AdminProductListSort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminProductListQueryRepository {

    Page<ProductSpuEntity> searchAdminProducts(
        Long merchantId,
        String status,
        String auditStatus,
        String publishStatus,
        String keyword,
        AdminProductListSort sort,
        Pageable pageable
    );
}
