package com.example.ecommerce.search.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
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

    @BeforeEach
    void setUp() {
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
    void projector_refresh_upserts_projection_row() {
        Long productId = productSpuRepository.findAll().get(0).getId();

        productSearchProjector.refresh(productId);

        StorefrontProductSearchEntity row = storefrontProductSearchRepository.findById(productId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(row.getTitle()).isEqualTo("search-hoodie");
        org.assertj.core.api.Assertions.assertThat(row.getMinPrice()).isEqualByComparingTo("129.00");
        org.assertj.core.api.Assertions.assertThat(row.getStockStatus()).isEqualTo("in_stock");
    }
}
