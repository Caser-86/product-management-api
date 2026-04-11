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
import com.example.ecommerce.search.domain.StorefrontProductSearchEntity;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StorefrontProductControllerTest {

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

    @Autowired
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @BeforeEach
    void setUp() {
        productWorkflowHistoryRepository.deleteAll();
        storefrontProductSearchRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        productSpuRepository.deleteAll();

        ProductSpuEntity hoodie = ProductSpuEntity.draft(2001L, "SPU-SCH-1", "search-hoodie", 33L);
        hoodie.addSku(ProductSkuEntity.of(2001L, "SKU-SCH-1", "{\"color\":\"black\"}", "search-hash-1"));
        var savedHoodie = productSpuRepository.save(hoodie);
        Long hoodieSkuId = savedHoodie.getSkus().get(0).getId();
        priceCurrentRepository.save(PriceCurrentEntity.of(hoodieSkuId, 2001L, new BigDecimal("199.00"), new BigDecimal("129.00")));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(hoodieSkuId, 2001L, 8));

        ProductSpuEntity sneakers = ProductSpuEntity.draft(2001L, "SPU-SCH-2", "search-sneakers", 66L);
        sneakers.addSku(ProductSkuEntity.of(2001L, "SKU-SCH-2", "{\"size\":\"42\"}", "search-hash-2"));
        var savedSneakers = productSpuRepository.save(sneakers);
        Long sneakersSkuId = savedSneakers.getSkus().get(0).getId();
        priceCurrentRepository.save(PriceCurrentEntity.of(sneakersSkuId, 2001L, new BigDecimal("399.00"), new BigDecimal("359.00")));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(sneakersSkuId, 2001L, 0));
    }

    @Test
    void searches_products_for_storefront() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            1001L,
            2001L,
            33L,
            "projection-hoodie",
            20001L,
            new BigDecimal("129.00"),
            new BigDecimal("199.00"),
            8,
            "in_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products")
                .param("keyword", "projection")
                .param("categoryId", "33"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("projection-hoodie"))
            .andExpect(jsonPath("$.data.items[0].minPrice").value(129.0))
            .andExpect(jsonPath("$.data.items[0].maxPrice").value(199.0))
            .andExpect(jsonPath("$.data.items[0].stockStatus").value("in_stock"));
    }

    @Test
    void filters_products_by_price_range() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            2001L,
            2001L,
            33L,
            "range-hoodie",
            30001L,
            new BigDecimal("99.00"),
            new BigDecimal("149.00"),
            6,
            "in_stock",
            "active",
            "published",
            "approved"
        ));
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            2002L,
            2001L,
            33L,
            "range-coat",
            30002L,
            new BigDecimal("299.00"),
            new BigDecimal("349.00"),
            2,
            "in_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products")
                .param("minPrice", "120")
                .param("maxPrice", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("range-hoodie"));
    }

    @Test
    void sorts_products_by_price_ascending() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            2003L,
            2001L,
            33L,
            "sort-budget",
            30003L,
            new BigDecimal("59.00"),
            new BigDecimal("99.00"),
            5,
            "in_stock",
            "active",
            "published",
            "approved"
        ));
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            2004L,
            2001L,
            33L,
            "sort-premium",
            30004L,
            new BigDecimal("259.00"),
            new BigDecimal("399.00"),
            5,
            "in_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products").param("sort", "price_asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].title").value("sort-budget"));
    }

    @Test
    void rejects_unknown_sort_value() throws Exception {
        mockMvc.perform(get("/products").param("sort", "random"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_invalid_price_range() throws Exception {
        mockMvc.perform(get("/products")
                .param("minPrice", "300")
                .param("maxPrice", "100"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void filters_to_only_in_stock_products() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            3001L,
            2001L,
            33L,
            "stock-hoodie",
            40001L,
            new BigDecimal("99.00"),
            new BigDecimal("149.00"),
            5,
            "in_stock",
            "active",
            "published",
            "approved"
        ));
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            3002L,
            2001L,
            33L,
            "stock-jacket",
            40002L,
            new BigDecimal("129.00"),
            new BigDecimal("199.00"),
            0,
            "out_of_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products").param("inStockOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("stock-hoodie"));
    }

    @Test
    void clamps_page_size_to_maximum() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            3003L,
            2001L,
            33L,
            "page-a",
            40003L,
            new BigDecimal("49.00"),
            new BigDecimal("79.00"),
            3,
            "in_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products").param("pageSize", "999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    void keeps_price_sort_stable_when_prices_tie() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            3004L,
            2001L,
            33L,
            "tie-first",
            40004L,
            new BigDecimal("88.00"),
            new BigDecimal("120.00"),
            4,
            "in_stock",
            "active",
            "published",
            "approved"
        ));
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            3005L,
            2001L,
            33L,
            "tie-second",
            40005L,
            new BigDecimal("88.00"),
            new BigDecimal("140.00"),
            4,
            "in_stock",
            "active",
            "published",
            "approved"
        ));

        mockMvc.perform(get("/products").param("sort", "price_asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].productId").value(3005))
            .andExpect(jsonPath("$.data.items[1].productId").value(3004));
    }

    @Test
    void excludes_non_visible_projection_rows() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            1002L,
            2001L,
            33L,
            "hidden-hoodie",
            20002L,
            new BigDecimal("129.00"),
            new BigDecimal("199.00"),
            8,
            "in_stock",
            "deleted",
            "unpublished",
            "pending"
        ));

        mockMvc.perform(get("/products")
                .param("keyword", "hidden"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void excludes_rows_that_are_not_published_or_not_approved() throws Exception {
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            1003L,
            2001L,
            33L,
            "hidden-unpublished",
            20003L,
            new BigDecimal("129.00"),
            new BigDecimal("199.00"),
            8,
            "in_stock",
            "active",
            "unpublished",
            "approved"
        ));
        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
            1004L,
            2001L,
            33L,
            "hidden-pending-audit",
            20004L,
            new BigDecimal("129.00"),
            new BigDecimal("199.00"),
            8,
            "in_stock",
            "active",
            "published",
            "pending"
        ));

        mockMvc.perform(get("/products")
                .param("keyword", "hidden-"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void projector_refresh_upserts_projection_row() {
        Long productId = productSpuRepository.findAll().get(0).getId();

        productSearchProjector.refresh(productId);

        StorefrontProductSearchEntity row = storefrontProductSearchRepository.findById(productId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(row.getTitle()).isEqualTo("search-hoodie");
        org.assertj.core.api.Assertions.assertThat(row.getMinPrice()).isEqualByComparingTo("129.00");
        org.assertj.core.api.Assertions.assertThat(row.getStockStatus()).isEqualTo("in_stock");
    }

    @Test
    void projector_refresh_aggregates_multi_sku_price_and_stock() {
        ProductSpuEntity bundle = ProductSpuEntity.draft(2001L, "SPU-SCH-3", "search-bundle", 77L);
        bundle.addSku(ProductSkuEntity.of(2001L, "SKU-SCH-3-A", "{\"size\":\"M\"}", "search-hash-3a"));
        bundle.addSku(ProductSkuEntity.of(2001L, "SKU-SCH-3-B", "{\"size\":\"L\"}", "search-hash-3b"));
        ProductSpuEntity savedBundle = productSpuRepository.save(bundle);

        Long firstSkuId = savedBundle.getSkus().get(0).getId();
        Long secondSkuId = savedBundle.getSkus().get(1).getId();
        priceCurrentRepository.save(PriceCurrentEntity.of(firstSkuId, 2001L, new BigDecimal("299.00"), new BigDecimal("259.00")));
        priceCurrentRepository.save(PriceCurrentEntity.of(secondSkuId, 2001L, new BigDecimal("349.00"), new BigDecimal("199.00")));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(firstSkuId, 2001L, 0));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(secondSkuId, 2001L, 4));

        productSearchProjector.refresh(savedBundle.getId());

        StorefrontProductSearchEntity row = storefrontProductSearchRepository.findById(savedBundle.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(row.getMinPrice()).isEqualByComparingTo("199.00");
        org.assertj.core.api.Assertions.assertThat(row.getMaxPrice()).isEqualByComparingTo("349.00");
        org.assertj.core.api.Assertions.assertThat(row.getAvailableQty()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(row.getStockStatus()).isEqualTo("in_stock");
    }
}
