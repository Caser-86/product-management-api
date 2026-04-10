package com.example.ecommerce.search.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
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

    @BeforeEach
    void setUp() {
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
        mockMvc.perform(get("/products")
                .param("keyword", "hoodie")
                .param("categoryId", "33"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("search-hoodie"))
            .andExpect(jsonPath("$.data.items[0].minPrice").value(129.0))
            .andExpect(jsonPath("$.data.items[0].maxPrice").value(199.0))
            .andExpect(jsonPath("$.data.items[0].stockStatus").value("in_stock"));
    }
}
