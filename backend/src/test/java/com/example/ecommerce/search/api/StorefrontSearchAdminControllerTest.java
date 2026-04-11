package com.example.ecommerce.search.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.example.ecommerce.search.application.ProductSearchProjector;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.support.AuthTestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StorefrontSearchAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private PriceCurrentRepository priceCurrentRepository;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private StorefrontProductSearchRepository storefrontProductSearchRepository;

    @Autowired
    private ProductSearchProjector productSearchProjector;

    @SpyBean
    private ProductSearchProjector productSearchProjectorSpy;

    @Autowired
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @Autowired
    private AuthTestTokens authTestTokens;

    @BeforeEach
    void setUp() {
        productWorkflowHistoryRepository.deleteAll();
        storefrontProductSearchRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        productSpuRepository.deleteAll();
    }

    @Test
    void admin_refresh_restores_missing_storefront_projection_row() throws Exception {
        Long productId = createStorefrontVisibleProduct();

        productSearchProjector.refresh(productId);
        assertThat(storefrontProductSearchRepository.findById(productId)).isPresent();
        storefrontProductSearchRepository.deleteById(productId);
        assertThat(storefrontProductSearchRepository.findById(productId)).isEmpty();

        mockMvc.perform(withBearer(post("/admin/search/storefront/products/{productId}/refresh", productId), platformAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.status").value("refreshed"));

        var row = storefrontProductSearchRepository.findById(productId).orElseThrow();
        assertThat(row.getTitle()).isEqualTo("search-admin-product");
        assertThat(row.getMinPrice()).isEqualByComparingTo("129.00");
        assertThat(row.getMaxPrice()).isEqualByComparingTo("199.00");
        assertThat(row.getAvailableQty()).isEqualTo(8);
        assertThat(row.getStockStatus()).isEqualTo("in_stock");
    }

    @Test
    void anonymous_cannot_refresh_projection() throws Exception {
        mockMvc.perform(post("/admin/search/storefront/products/{productId}/refresh", 999L))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void admin_rebuild_restores_missing_projection_rows() throws Exception {
        Long firstProductId = createStorefrontVisibleProduct();
        Long secondProductId = createStorefrontVisibleProduct();

        productSearchProjector.refresh(firstProductId);
        productSearchProjector.refresh(secondProductId);
        storefrontProductSearchRepository.deleteAll();

        mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), platformAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedCount").value(2))
            .andExpect(jsonPath("$.data.successCount").value(2))
            .andExpect(jsonPath("$.data.failureCount").value(0));

        assertThat(storefrontProductSearchRepository.findById(firstProductId)).isPresent();
        assertThat(storefrontProductSearchRepository.findById(secondProductId)).isPresent();
        assertThat(storefrontProductSearchRepository.findAll()).hasSize(2);
    }

    @Test
    void rebuild_reports_failures_without_aborting() throws Exception {
        Long validProductId = createStorefrontVisibleProduct();
        Long brokenProductId = createStorefrontVisibleProduct();

        storefrontProductSearchRepository.deleteAll();
        doThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "projection rebuild failed"))
            .when(productSearchProjectorSpy)
            .refresh(brokenProductId);

        mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), platformAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedCount").value(2))
            .andExpect(jsonPath("$.data.successCount").value(1))
            .andExpect(jsonPath("$.data.failureCount").value(1))
            .andExpect(jsonPath("$.data.failures[0].productId").value(brokenProductId))
            .andExpect(jsonPath("$.data.failures[0].errorCode").value("PRODUCT_NOT_FOUND"));

        assertThat(storefrontProductSearchRepository.findById(validProductId)).isPresent();
    }

    @Test
    void merchant_admin_cannot_rebuild_projection() throws Exception {
        mockMvc.perform(withBearer(post("/admin/search/storefront/rebuild"), merchantAdminToken()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    private long createStorefrontVisibleProduct() {
        ProductSpuEntity product = ProductSpuEntity.draft(
            2001L,
            "SPU-SEARCH-ADMIN-" + System.nanoTime(),
            "search-admin-product",
            66L
        );
        product.addSku(ProductSkuEntity.of(2001L, "SKU-SEARCH-ADMIN-" + System.nanoTime(), "{\"color\":\"black\"}", "search-admin-hash"));
        product.submitForReview(LocalDateTime.of(2026, 4, 11, 10, 0, 0));
        product.approve(9001L, "approved for storefront", LocalDateTime.of(2026, 4, 11, 10, 10, 0));
        product.publish(9001L, LocalDateTime.of(2026, 4, 11, 10, 20, 0));

        ProductSpuEntity savedProduct = productSpuRepository.save(product);
        Long skuId = savedProduct.getSkus().get(0).getId();
        priceCurrentRepository.save(PriceCurrentEntity.of(skuId, 2001L, new BigDecimal("199.00"), new BigDecimal("129.00")));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(skuId, 2001L, 8));
        return savedProduct.getId();
    }

    private String platformAdminToken() {
        return authTestTokens.platformAdminToken();
    }

    private String merchantAdminToken() {
        return authTestTokens.merchantAdminToken(9002L, 2001L);
    }

    private MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder requestBuilder, String token) {
        return requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
