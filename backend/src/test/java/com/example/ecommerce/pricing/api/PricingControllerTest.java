package com.example.ecommerce.pricing.api;

import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.pricing.domain.PriceScheduleRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PricingControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private PriceCurrentRepository priceCurrentRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private PriceScheduleRepository priceScheduleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long skuId;

    @BeforeEach
    void setUp() {
        priceScheduleRepository.deleteAll();
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
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
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

        mockMvc.perform(get("/admin/skus/{skuId}/price-history", skuId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].reason").value("weekend campaign"))
            .andExpect(jsonPath("$.data.items[0].newPrice").value("{\"listPrice\":189.00,\"salePrice\":149.00}"));
    }

    @Test
    void creates_and_applies_scheduled_price() throws Exception {
        MvcResult createScheduleResult = mockMvc.perform(post("/admin/skus/{skuId}/price-schedules", skuId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listPrice": 299.00,
                      "salePrice": 239.00,
                      "effectiveAt": "%s",
                      "reason": "scheduled release",
                      "operatorId": 7001
                    }
                    """.formatted(LocalDateTime.now().minusMinutes(5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("pending"))
            .andReturn();

        long scheduleId = objectMapper.readTree(createScheduleResult.getResponse().getContentAsString())
            .path("data")
            .path("scheduleId")
            .asLong();

        mockMvc.perform(post("/admin/price-schedules/{scheduleId}/apply", scheduleId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/admin/skus/{skuId}/price-history", skuId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].changeType").value("scheduled"))
            .andExpect(jsonPath("$.data.items[0].reason").value("scheduled release"));
    }

    @Test
    void merchant_admin_cannot_update_price_for_other_merchant_sku() throws Exception {
        ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-PRC-2", "pricing-foreign", 44L);
        foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-PRC-2", "{\"color\":\"white\"}", "pricing-hash-2"));
        Long foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

        mockMvc.perform(patch("/admin/skus/{skuId}/prices", foreignSkuId)
                .header(USER_ID_HEADER, "9002")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listPrice": 189.00,
                      "salePrice": 149.00,
                      "reason": "weekend campaign",
                      "operatorId": 501
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }
}
