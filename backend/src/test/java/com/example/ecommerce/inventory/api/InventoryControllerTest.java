package com.example.ecommerce.inventory.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @BeforeEach
    void setUp() {
        inventoryBalanceRepository.deleteAll();
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(1L, 2001L, 10));
    }

    @Test
    void reserves_inventory_and_then_confirms_it() throws Exception {
        mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8001-attempt-1",
                      "bizId": "ORDER-8001",
                      "items": [{"skuId": 1, "quantity": 2}]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("reserved"));

        mockMvc.perform(post("/inventory/reservations/ORDER-8001/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-8001","operatorType":"system"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/admin/skus/1/inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.soldQty").value(2));
    }
}
