package com.example.ecommerce.inventory.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        inventoryReservationRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(1L, 2001L, 10));
    }

    @Test
    void reserves_inventory_and_then_confirms_it() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8001-attempt-1",
                      "bizId": "ORDER-8001",
                      "items": [{"skuId": 1, "quantity": 2}]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("reserved"))
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

        mockMvc.perform(get("/admin/skus/1/inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.soldQty").value(2));
    }

    @Test
    void rejects_multi_item_reservation_requests() throws Exception {
        mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8002-attempt-1",
                      "bizId": "ORDER-8002",
                      "items": [
                        {"skuId": 1, "quantity": 1},
                        {"skuId": 2, "quantity": 1}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void confirm_is_idempotent_for_same_reservation() throws Exception {
        MvcResult reserveResult = mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8003-attempt-1",
                      "bizId": "ORDER-8003",
                      "items": [{"skuId": 1, "quantity": 2}]
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String reservationId = objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();

        String confirmPayload = """
            {"bizId":"ORDER-8003","operatorType":"system"}
            """;

        mockMvc.perform(post("/inventory/reservations/{reservationId}/confirm", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isOk());

        mockMvc.perform(post("/inventory/reservations/{reservationId}/confirm", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isOk());

        mockMvc.perform(get("/admin/skus/1/inventory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.soldQty").value(2));
    }
}
