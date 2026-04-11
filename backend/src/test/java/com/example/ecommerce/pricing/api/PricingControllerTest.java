package com.example.ecommerce.pricing.api;

import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.pricing.domain.PriceScheduleRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.example.ecommerce.support.AuthTestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingControllerTest {

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
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthTestTokens authTestTokens;

    private Long skuId;

    @BeforeEach
    void setUp() {
        priceScheduleRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        productWorkflowHistoryRepository.deleteAll();
        productSpuRepository.deleteAll();

        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-PRC-1", "pricing-demo", 33L);
        spu.addSku(ProductSkuEntity.of(2001L, "SKU-PRC-1", "{\"color\":\"black\"}", "pricing-hash-1"));
        skuId = productSpuRepository.save(spu).getSkus().get(0).getId();
    }

    @Test
    void updates_price_and_records_history() throws Exception {
        updatePrice(skuId, 189.00, 149.00, "weekend campaign", 501L);

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].reason").value("weekend campaign"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(20))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].newPrice.listPrice").value(189.00))
            .andExpect(jsonPath("$.data.items[0].newPrice.salePrice").value(149.00));
    }

    @Test
    void creates_and_applies_scheduled_price() throws Exception {
        MvcResult createScheduleResult = mockMvc.perform(withBearer(post("/admin/skus/{skuId}/price-schedules", skuId), platformAdminToken(9001L, 2001L))
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

        mockMvc.perform(withBearer(post("/admin/price-schedules/{scheduleId}/apply", scheduleId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].changeType").value("scheduled"))
            .andExpect(jsonPath("$.data.items[0].reason").value("scheduled release"))
            .andExpect(jsonPath("$.data.items[0].newPrice.listPrice").value(299.00))
            .andExpect(jsonPath("$.data.items[0].newPrice.salePrice").value(239.00));
    }

    @Test
    void merchant_admin_cannot_update_price_for_other_merchant_sku() throws Exception {
        ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-PRC-2", "pricing-foreign", 44L);
        foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-PRC-2", "{\"color\":\"white\"}", "pricing-hash-2"));
        Long foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

        mockMvc.perform(withBearer(patch("/admin/skus/{skuId}/prices", foreignSkuId), merchantAdminToken(9002L, 2001L))
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

    @Test
    void price_history_returns_typed_price_snapshots() throws Exception {
        updatePrice(skuId, 189.00, 149.00, "launch price", 501L);
        updatePrice(skuId, 199.00, 159.00, "raise price", 502L);

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].changeType").value("manual"))
            .andExpect(jsonPath("$.data.items[0].newPrice.listPrice").value(199.00))
            .andExpect(jsonPath("$.data.items[0].newPrice.salePrice").value(159.00))
            .andExpect(jsonPath("$.data.items[0].oldPrice.listPrice").value(189.00))
            .andExpect(jsonPath("$.data.items[0].oldPrice.salePrice").value(149.00));
    }

    @Test
    void price_history_supports_pagination() throws Exception {
        updatePrice(skuId, 100.00, 90.00, "price-1", 501L);
        updatePrice(skuId, 110.00, 95.00, "price-2", 502L);
        updatePrice(skuId, 120.00, 99.00, "price-3", 503L);

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId)
                .param("page", "2")
                .param("pageSize", "1"), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.pageSize").value(1))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].reason").value("price-2"));
    }

    @Test
    void price_history_clamps_page_size_to_maximum() throws Exception {
        updatePrice(skuId, 100.00, 90.00, "price-1", 501L);

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", skuId)
                .param("pageSize", "999"), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    void merchant_admin_cannot_read_price_history_for_other_merchant_sku() throws Exception {
        ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-PRC-HISTORY-2", "pricing-foreign", 44L);
        foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-PRC-HISTORY-2", "{\"color\":\"white\"}", "pricing-hash-history-2"));
        Long foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/price-history", foreignSkuId), merchantAdminToken(9002L, 2001L)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    private String platformAdminToken(long userId, long merchantId) {
        return authTestTokens.platformAdminToken(userId, merchantId);
    }

    private String merchantAdminToken(long userId, long merchantId) {
        return authTestTokens.merchantAdminToken(userId, merchantId);
    }

    private MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder requestBuilder, String token) {
        return requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void updatePrice(Long skuId, double listPrice, double salePrice, String reason, long operatorId) throws Exception {
        mockMvc.perform(withBearer(patch("/admin/skus/{skuId}/prices", skuId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listPrice": %.2f,
                      "salePrice": %.2f,
                      "reason": "%s",
                      "operatorId": %d
                    }
                    """.formatted(listPrice, salePrice, reason, operatorId)))
            .andExpect(status().isOk());
    }
}
