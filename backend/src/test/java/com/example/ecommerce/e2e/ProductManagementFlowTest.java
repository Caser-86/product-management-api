package com.example.ecommerce.e2e;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryLedgerRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.pricing.domain.PriceScheduleRepository;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import com.example.ecommerce.search.domain.StorefrontProductSearchRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProductManagementFlowTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @Autowired
    private StorefrontProductSearchRepository storefrontProductSearchRepository;

    @Autowired
    private PriceCurrentRepository priceCurrentRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private PriceScheduleRepository priceScheduleRepository;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private InventoryLedgerRepository inventoryLedgerRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @BeforeEach
    void setUp() {
        productWorkflowHistoryRepository.deleteAll();
        storefrontProductSearchRepository.deleteAll();
        priceScheduleRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        inventoryReservationRepository.deleteAll();
        inventoryLedgerRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        productSpuRepository.deleteAll();
    }

    @Test
    void admin_can_create_product_adjust_inventory_update_price_and_search() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String productTitle = "flow-hoodie-" + suffix;
        String idempotencyKey = "order-" + suffix + "-attempt-1";
        String bizId = "ORDER-" + suffix;
        MvcResult createResult = mockMvc.perform(post("/admin/products")
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "merchantId": 2001,
                      "productType": "merchant",
                      "title": "%s",
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
                    """.formatted(productTitle)))
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
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "%s",
                      "bizId": "%s",
                      "items": [{"skuId": %d, "quantity": 1}]
                    }
                    """.formatted(idempotencyKey, bizId, skuId)))
            .andExpect(status().isOk())
            .andReturn();

        String reservationId = objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();

        mockMvc.perform(post("/inventory/reservations/{reservationId}/confirm", reservationId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"%s","operatorType":"system"}
                    """.formatted(bizId)))
            .andExpect(status().isOk());

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

        mockMvc.perform(post("/admin/products/{productId}/submit-for-review", productId)
                .header(USER_ID_HEADER, "9101")
                .header(ROLE_HEADER, "MERCHANT_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "submit product for platform review"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("pending"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));

        mockMvc.perform(post("/admin/products/{productId}/approve", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "approved for storefront"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("approved"))
            .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));

        mockMvc.perform(post("/admin/products/{productId}/publish", productId)
                .header(USER_ID_HEADER, "9001")
                .header(ROLE_HEADER, "PLATFORM_ADMIN")
                .header(MERCHANT_ID_HEADER, "2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "publish to storefront"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.auditStatus").value("approved"))
            .andExpect(jsonPath("$.data.publishStatus").value("published"));

        mockMvc.perform(get("/products").param("keyword", "flow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value(productTitle))
            .andExpect(jsonPath("$.data.items[0].minPrice").value(149.0))
            .andExpect(jsonPath("$.data.items[0].stockStatus").value("in_stock"));
    }
}
