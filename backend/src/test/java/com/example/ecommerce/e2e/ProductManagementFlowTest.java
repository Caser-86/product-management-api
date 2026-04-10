package com.example.ecommerce.e2e;

import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProductManagementFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Test
    void admin_can_create_product_adjust_inventory_update_price_and_search() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "merchantId": 2001,
                      "productType": "merchant",
                      "title": "flow-hoodie",
                      "categoryId": 33,
                      "skus": [
                        {
                          "skuCode": "SKU-FLOW-BLK-M",
                          "specSnapshot": "{\\"color\\":\\"black\\",\\"size\\":\\"M\\"}",
                          "specHash": "spec-hash-flow",
                          "initialStock": 10
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode createPayload = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long productId = createPayload.path("data").path("id").asLong();
        long skuId = productSpuRepository.findWithSkusById(productId)
            .orElseThrow()
            .getSkus()
            .get(0)
            .getId();

        MvcResult reserveResult = mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8001-attempt-1",
                      "bizId": "ORDER-8001",
                      "items": [{"skuId": %d, "quantity": 1}]
                    }
                    """.formatted(skuId)))
            .andExpect(status().isOk())
            .andReturn();

        String reservationId = objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();

        mockMvc.perform(post("/inventory/reservations/{reservationId}/confirm", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-8001","operatorType":"system"}
                    """))
            .andExpect(status().isOk());

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

        mockMvc.perform(get("/products").param("keyword", "flow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].title").exists());
    }
}
