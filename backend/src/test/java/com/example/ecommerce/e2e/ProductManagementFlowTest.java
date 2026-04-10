package com.example.ecommerce.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void admin_can_create_product_adjust_inventory_update_price_and_search() throws Exception {
        mockMvc.perform(post("/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "merchantId": 2001,
                      "productType": "merchant",
                      "title": "男士连帽卫衣",
                      "categoryId": 33,
                      "skus": [
                        {
                          "skuCode": "SKU-1001-BLK-M",
                          "specSnapshot": "{\\"颜色\\":\\"黑色\\",\\"尺寸\\":\\"M\\"}",
                          "specHash": "spec-hash-flow",
                          "initialStock": 10
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8001-attempt-1",
                      "bizId": "ORDER-8001",
                      "items": [{"skuId": 1, "quantity": 1}]
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/inventory/reservations/ORDER-8001/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-8001","operatorType":"system"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/admin/skus/1/prices")
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

        mockMvc.perform(get("/products").param("keyword", "卫衣"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].title").exists());
    }
}
