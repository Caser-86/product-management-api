package com.example.ecommerce.inventory.api;

import com.example.ecommerce.inventory.domain.InventoryBalanceEntity;
import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryLedgerRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private InventoryLedgerRepository inventoryLedgerRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private PriceCurrentRepository priceCurrentRepository;

    @Autowired
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthTestTokens authTestTokens;

    private Long ownSkuId;
    private Long foreignSkuId;

    @BeforeEach
    void setUp() {
        inventoryReservationRepository.deleteAll();
        inventoryLedgerRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        productWorkflowHistoryRepository.deleteAll();
        priceCurrentRepository.deleteAll();
        productSpuRepository.deleteAll();

        ProductSpuEntity ownSpu = ProductSpuEntity.draft(2001L, "SPU-INV-OWN", "inventory-own", 33L);
        ownSpu.addSku(ProductSkuEntity.of(2001L, "SKU-INV-OWN", "{\"color\":\"black\"}", "inventory-hash-own"));
        ownSkuId = productSpuRepository.save(ownSpu).getSkus().get(0).getId();

        ProductSpuEntity foreignSpu = ProductSpuEntity.draft(4001L, "SPU-INV-FOREIGN", "inventory-foreign", 33L);
        foreignSpu.addSku(ProductSkuEntity.of(4001L, "SKU-INV-FOREIGN", "{\"color\":\"white\"}", "inventory-hash-foreign"));
        foreignSkuId = productSpuRepository.save(foreignSpu).getSkus().get(0).getId();

        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(ownSkuId, 2001L, 10));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(foreignSkuId, 4001L, 5));
    }

    @Test
    void reserves_inventory_and_then_confirms_it() throws Exception {
        MvcResult reserveResult = mockMvc.perform(withBearer(post("/inventory/reservations"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8001-attempt-1",
                      "bizId": "ORDER-8001",
                      "items": [{"skuId": %d, "quantity": 2}]
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("reserved"))
            .andReturn();

        String reservationId = objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/confirm", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-8001","operatorType":"system"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.soldQty").value(2));
    }

    @Test
    void rejects_multi_item_reservation_requests() throws Exception {
        mockMvc.perform(withBearer(post("/inventory/reservations"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8002-attempt-1",
                      "bizId": "ORDER-8002",
                      "items": [
                        {"skuId": %d, "quantity": 1},
                        {"skuId": %d, "quantity": 1}
                      ]
                    }
                    """.formatted(ownSkuId, foreignSkuId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void confirm_is_idempotent_for_same_reservation() throws Exception {
        MvcResult reserveResult = mockMvc.perform(withBearer(post("/inventory/reservations"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8003-attempt-1",
                      "bizId": "ORDER-8003",
                      "items": [{"skuId": %d, "quantity": 2}]
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk())
            .andReturn();

        String reservationId = objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();

        String confirmPayload = """
            {"bizId":"ORDER-8003","operatorType":"system"}
            """;

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/confirm", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isOk());

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/confirm", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmPayload))
            .andExpect(status().isOk());

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.soldQty").value(2));
    }

    @Test
    void releases_reserved_inventory_back_to_available() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REL-1", 2);

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-REL-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("released"));

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableQty").value(10))
            .andExpect(jsonPath("$.data.reservedQty").value(0));
    }

    @Test
    void refunds_with_restock_move_sold_back_to_available() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REFUND-1", 2);
        confirmReservation(reservationId, "ORDER-REFUND-1");

        mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"ORDER-REFUND-1",
                      "skuId": %d,
                      "quantity": 1,
                      "restock": true,
                      "reason": "customer cancellation",
                      "operatorId": 9001
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableQty").value(9))
            .andExpect(jsonPath("$.data.soldQty").value(1))
            .andExpect(jsonPath("$.data.restock").value(true));
    }

    @Test
    void confirmed_reservation_cannot_be_released() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REL-2", 1);
        confirmReservation(reservationId, "ORDER-REL-2");

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-REL-2"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void refund_without_restock_only_reduces_sold_quantity() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REFUND-2", 2);
        confirmReservation(reservationId, "ORDER-REFUND-2");

        mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"ORDER-REFUND-2",
                      "skuId": %d,
                      "quantity": 1,
                      "restock": false,
                      "reason": "damaged return",
                      "operatorId": 9001
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableQty").value(8))
            .andExpect(jsonPath("$.data.soldQty").value(1))
            .andExpect(jsonPath("$.data.restock").value(false));
    }

    @Test
    void anonymous_cannot_release_inventory() throws Exception {
        mockMvc.perform(post("/inventory/reservations/{reservationId}/release", "missing-auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-REL-3"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void merchant_admin_cannot_refund_other_merchant_inventory() throws Exception {
        String reservationId = reserveInventory(foreignSkuId, "ORDER-REFUND-3", 1);
        confirmReservation(reservationId, "ORDER-REFUND-3");

        mockMvc.perform(withBearer(post("/admin/inventory/refunds"), merchantAdminToken(9002L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"ORDER-REFUND-3",
                      "skuId": %d,
                      "quantity": 1,
                      "restock": true,
                      "reason": "cross-merchant refund",
                      "operatorId": 9002
                    }
                    """.formatted(foreignSkuId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    @Test
    void adjusts_inventory_for_admin_operations() throws Exception {
        mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "delta": 5,
                      "reason": "manual restock",
                      "operatorId": 9001
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableQty").value(15))
            .andExpect(jsonPath("$.data.totalQty").value(15));

        mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "delta": -3,
                      "reason": "damage writeoff",
                      "operatorId": 9001
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableQty").value(12))
            .andExpect(jsonPath("$.data.totalQty").value(12));

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalQty").value(12))
            .andExpect(jsonPath("$.data.availableQty").value(12))
            .andExpect(jsonPath("$.data.reservedQty").value(0))
            .andExpect(jsonPath("$.data.soldQty").value(0));
    }

    @Test
    void reserve_requires_authentication_headers() throws Exception {
        mockMvc.perform(post("/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8010-attempt-1",
                      "bizId": "ORDER-8010",
                      "items": [{"skuId": %d, "quantity": 1}]
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void merchant_admin_cannot_adjust_inventory_for_other_merchant_sku() throws Exception {
        mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", foreignSkuId), merchantAdminToken(9002L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "delta": 5,
                      "reason": "manual restock",
                      "operatorId": 9002
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_MERCHANT_SCOPE_DENIED"));
    }

    @Test
    void reserve_creates_inventory_ledger_entry() throws Exception {
        mockMvc.perform(withBearer(post("/inventory/reservations"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "order-8011-attempt-1",
                      "bizId": "ORDER-8011",
                      "items": [{"skuId": %d, "quantity": 2}]
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk());

        assertThat(inventoryLedgerRepository.findAll()).hasSize(1);
        assertThat(inventoryLedgerRepository.findAll().get(0).getDeltaAvailable()).isEqualTo(-2);
        assertThat(inventoryLedgerRepository.findAll().get(0).getDeltaReserved()).isEqualTo(2);
    }

    @Test
    void inventory_history_returns_ledger_entries() throws Exception {
        mockMvc.perform(withBearer(post("/admin/skus/{skuId}/inventory/adjustments", ownSkuId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "delta": 3,
                      "reason": "manual restock",
                      "operatorId": 9001
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].bizType").value("adjust"))
            .andExpect(jsonPath("$.data.items[0].deltaAvailable").value(3));
    }

    @Test
    void release_creates_inventory_ledger_entry() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REL-4", 2);

        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/release", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"ORDER-REL-4"}
                    """))
            .andExpect(status().isOk());

        assertThat(inventoryLedgerRepository.findAll())
            .anySatisfy(ledger -> {
                assertThat(ledger.getBizType()).isEqualTo("release");
                assertThat(ledger.getDeltaAvailable()).isEqualTo(2);
                assertThat(ledger.getDeltaReserved()).isEqualTo(-2);
            });
    }

    @Test
    void refund_history_includes_refund_business_types() throws Exception {
        String reservationId = reserveInventory(ownSkuId, "ORDER-REFUND-4", 2);
        confirmReservation(reservationId, "ORDER-REFUND-4");

        mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"ORDER-REFUND-4",
                      "skuId": %d,
                      "quantity": 1,
                      "restock": false,
                      "reason": "damaged return",
                      "operatorId": 9001
                    }
                    """.formatted(ownSkuId)))
            .andExpect(status().isOk());

        mockMvc.perform(withBearer(get("/admin/skus/{skuId}/inventory/history", ownSkuId), platformAdminToken(9001L, 2001L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].bizType").value("refund_no_restock"));
    }

    @Test
    void refund_restock_updates_storefront_stock_status() throws Exception {
        Long storefrontSkuId = createStorefrontVisibleSku("inventory-restock-visible", 1, new BigDecimal("149.00"), new BigDecimal("99.00"));

        String reservationId = reserveInventory(storefrontSkuId, "ORDER-REFUND-5", 1);
        confirmReservation(reservationId, "ORDER-REFUND-5");

        mockMvc.perform(get("/products")
                .param("keyword", "inventory-restock-visible"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].stockStatus").value("out_of_stock"));

        mockMvc.perform(withBearer(post("/admin/inventory/refunds"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"ORDER-REFUND-5",
                      "skuId": %d,
                      "quantity": 1,
                      "restock": true,
                      "reason": "customer return",
                      "operatorId": 9001
                    }
                    """.formatted(storefrontSkuId)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/products")
                .param("keyword", "inventory-restock-visible"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].stockStatus").value("in_stock"));
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

    private String reserveInventory(Long skuId, String bizId, int quantity) throws Exception {
        MvcResult reserveResult = mockMvc.perform(withBearer(post("/inventory/reservations"), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idempotencyKey": "%s-attempt-1",
                      "bizId": "%s",
                      "items": [{"skuId": %d, "quantity": %d}]
                    }
                    """.formatted(bizId, bizId, skuId, quantity)))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(reserveResult.getResponse().getContentAsString())
            .path("data")
            .path("reservationId")
            .asText();
    }

    private void confirmReservation(String reservationId, String bizId) throws Exception {
        mockMvc.perform(withBearer(post("/inventory/reservations/{reservationId}/confirm", reservationId), platformAdminToken(9001L, 2001L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizId":"%s","operatorType":"system"}
                    """.formatted(bizId)))
            .andExpect(status().isOk());
    }

    private Long createStorefrontVisibleSku(String title, int quantity, BigDecimal listPrice, BigDecimal salePrice) {
        ProductSpuEntity product = ProductSpuEntity.draft(2001L, "SPU-INV-VIS-" + System.nanoTime(), title, 88L);
        product.addSku(ProductSkuEntity.of(2001L, "SKU-INV-VIS-" + System.nanoTime(), "{\"size\":\"M\"}", "inventory-visible-hash"));
        product.submitForReview(java.time.LocalDateTime.of(2026, 4, 11, 12, 0, 0));
        product.approve(9001L, "approved for storefront", java.time.LocalDateTime.of(2026, 4, 11, 12, 5, 0));
        product.publish(9001L, java.time.LocalDateTime.of(2026, 4, 11, 12, 10, 0));

        ProductSpuEntity saved = productSpuRepository.save(product);
        Long skuId = saved.getSkus().get(0).getId();
        priceCurrentRepository.save(PriceCurrentEntity.of(skuId, 2001L, listPrice, salePrice));
        inventoryBalanceRepository.save(InventoryBalanceEntity.initial(skuId, 2001L, quantity));
        return skuId;
    }
}
