package com.example.ecommerce.pricing.api;

import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private PriceCurrentRepository priceCurrentRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    private Long skuId;

    @BeforeEach
    void setUp() {
        priceHistoryRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        productSpuRepository.deleteAll();

        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-PRC-1", "pricing-demo", 33L);
        spu.addSku(ProductSkuEntity.of(2001L, "SKU-PRC-1", "{\"color\":\"black\"}", "pricing-hash-1"));
        skuId = productSpuRepository.save(spu).getSkus().get(0).getId();
    }

    @Test
    void updates_price_and_records_history() throws Exception {
        mockMvc.perform(patch("/admin/skus/{skuId}/prices", skuId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listPrice": 189.00,
                      "salePrice": 149.00,
                      "reason": "weekend campaign",
                      "operatorId": 501
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/admin/skus/{skuId}/price-history", skuId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].reason").value("weekend campaign"))
            .andExpect(jsonPath("$.data.items[0].newPrice").value("{\"listPrice\":189.00,\"salePrice\":149.00}"));
    }
}
