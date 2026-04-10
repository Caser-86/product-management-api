# Storefront Search Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a MySQL-backed storefront search projection and switch `GET /products` to read from that projection instead of aggregating transactional tables at request time.

**Architecture:** Introduce a dedicated `storefront_product_search` projection table plus a small search projection module with entity, repository, and refresh logic. Product, inventory, and pricing writes refresh the projection synchronously in the same application, and `StorefrontSearchService` reads directly from the projection table.

**Tech Stack:** Spring Boot 3.3, Spring Data JPA, Flyway, MySQL/H2, MockMvc, JUnit 5

---

## File Map

### New Files

- `backend/src/main/resources/db/migration/V6__create_storefront_product_search_table.sql`
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java`
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`

### Modified Files

- `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
- `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`
- `backend/README.md`

### Existing Files To Read During Implementation

- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuRepository.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSkuEntity.java`
- `backend/src/main/java/com/example/ecommerce/pricing/domain/PriceCurrentRepository.java`
- `backend/src/main/java/com/example/ecommerce/inventory/domain/InventoryBalanceRepository.java`

## Task 1: Add the Storefront Search Projection Table and Entity

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_storefront_product_search_table.sql`
- Create: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java`
- Create: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Write the failing storefront projection test**

```java
@Test
void search_reads_from_projection_table() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.visible(
        1001L,
        2001L,
        33L,
        "projection-hoodie",
        20001L,
        new BigDecimal("129.00"),
        new BigDecimal("199.00"),
        8
    ));

    mockMvc.perform(get("/products")
            .param("keyword", "projection")
            .param("categoryId", "33"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].title").value("projection-hoodie"));
}
```

- [ ] **Step 2: Run the search test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: FAIL because the projection table, entity, and repository do not exist yet.

- [ ] **Step 3: Add the migration**

```sql
CREATE TABLE storefront_product_search (
    product_id BIGINT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    primary_sku_id BIGINT NOT NULL,
    min_price DECIMAL(18,2) NOT NULL,
    max_price DECIMAL(18,2) NOT NULL,
    available_qty INT NOT NULL,
    stock_status VARCHAR(20) NOT NULL,
    product_status VARCHAR(20) NOT NULL,
    publish_status VARCHAR(20) NOT NULL,
    audit_status VARCHAR(20) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_storefront_product_search_visibility
    ON storefront_product_search (product_status, publish_status, audit_status, category_id);
```

- [ ] **Step 4: Add the projection entity and repository**

```java
@Entity
@Table(name = "storefront_product_search")
public class StorefrontProductSearchEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Column(name = "primary_sku_id", nullable = false)
    private Long primarySkuId;

    @Column(name = "min_price", nullable = false)
    private BigDecimal minPrice;

    @Column(name = "max_price", nullable = false)
    private BigDecimal maxPrice;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "stock_status", nullable = false)
    private String stockStatus;

    @Column(name = "product_status", nullable = false)
    private String productStatus;

    @Column(name = "publish_status", nullable = false)
    private String publishStatus;

    @Column(name = "audit_status", nullable = false)
    private String auditStatus;
}
```

```java
public interface StorefrontProductSearchRepository extends JpaRepository<StorefrontProductSearchEntity, Long> {
}
```

- [ ] **Step 5: Run the search test to verify the schema and types compile**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: FAIL later in the read path because the service still reads transactional tables instead of the projection.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__create_storefront_product_search_table.sql backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "feat: add storefront search projection schema"
```

## Task 2: Switch Storefront Search Reads to the Projection

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Extend the failing search tests for visibility rules**

```java
@Test
void search_excludes_non_visible_projection_rows() throws Exception {
    storefrontProductSearchRepository.save(StorefrontProductSearchEntity.hidden(
        1002L,
        2001L,
        33L,
        "hidden-hoodie",
        20002L,
        new BigDecimal("129.00"),
        new BigDecimal("199.00"),
        8,
        "deleted",
        "unpublished",
        "pending"
    ));

    mockMvc.perform(get("/products").param("keyword", "hidden"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
}
```

- [ ] **Step 2: Run the search tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: FAIL because `StorefrontSearchService` still uses `ProductSpuRepository`, `PriceCurrentRepository`, and `InventoryBalanceRepository`.

- [ ] **Step 3: Add repository query methods for projection search**

```java
Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatusAndTitleContainingIgnoreCase(
    String productStatus,
    String publishStatus,
    String auditStatus,
    String title,
    Pageable pageable
);
```

```java
Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatusAndTitleContainingIgnoreCaseAndCategoryId(
    String productStatus,
    String publishStatus,
    String auditStatus,
    String title,
    Long categoryId,
    Pageable pageable
);
```

- [ ] **Step 4: Rewrite the search service to read the projection**

```java
@Transactional(readOnly = true)
public StorefrontSearchResponse search(String keyword, Long categoryId, int page, int pageSize) {
    int safePage = Math.max(page, 1);
    int safePageSize = Math.max(pageSize, 1);
    PageRequest pageable = PageRequest.of(safePage - 1, safePageSize);
    Page<StorefrontProductSearchEntity> pageResult = query(keyword, categoryId, pageable);

    List<StorefrontSearchResponse.Item> items = pageResult.getContent().stream()
        .map(row -> new StorefrontSearchResponse.Item(
            row.getProductId(),
            row.getTitle(),
            row.getMinPrice().doubleValue(),
            row.getMaxPrice().doubleValue(),
            row.getStockStatus()
        ))
        .toList();

    return new StorefrontSearchResponse(items, safePage, safePageSize, pageResult.getTotalElements());
}
```

- [ ] **Step 5: Run the search tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "feat: read storefront search from projection"
```

## Task 3: Implement Projection Refresh Logic

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`

- [ ] **Step 1: Write the failing projection refresh tests**

```java
@Test
void projector_upserts_row_from_product_price_and_inventory() {
    projector.refresh(productId);

    StorefrontProductSearchEntity row = storefrontProductSearchRepository.findById(productId).orElseThrow();
    assertThat(row.getTitle()).isEqualTo("search-hoodie");
    assertThat(row.getMinPrice()).isEqualByComparingTo("129.00");
    assertThat(row.getAvailableQty()).isEqualTo(8);
    assertThat(row.getStockStatus()).isEqualTo("in_stock");
}
```

- [ ] **Step 2: Run the projection tests to verify they fail**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: FAIL because `ProductSearchProjector` is still a stub.

- [ ] **Step 3: Implement the projector refresh path**

```java
@Component
public class ProductSearchProjector {

    private final ProductSpuRepository productSpuRepository;
    private final PriceCurrentRepository priceCurrentRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StorefrontProductSearchRepository storefrontProductSearchRepository;

    @Transactional
    public void refresh(Long productId) {
        ProductSpuEntity spu = productSpuRepository.findWithSkusById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found"));
        if (spu.getSkus().isEmpty()) {
            storefrontProductSearchRepository.deleteById(productId);
            return;
        }

        ProductSkuEntity sku = spu.getSkus().get(0);
        PriceCurrentEntity price = priceCurrentRepository.findById(sku.getId()).orElse(null);
        InventoryBalanceEntity inventory = inventoryBalanceRepository.findById(sku.getId()).orElse(null);

        storefrontProductSearchRepository.save(StorefrontProductSearchEntity.from(spu, sku, price, inventory));
    }
}
```

- [ ] **Step 4: Add convenience factory/update methods on the projection entity**

```java
public static StorefrontProductSearchEntity from(
    ProductSpuEntity spu,
    ProductSkuEntity sku,
    PriceCurrentEntity price,
    InventoryBalanceEntity inventory
) {
    StorefrontProductSearchEntity entity = new StorefrontProductSearchEntity();
    entity.productId = spu.getId();
    entity.merchantId = spu.getMerchantId();
    entity.categoryId = spu.getCategoryId();
    entity.title = spu.getTitle();
    entity.primarySkuId = sku.getId();
    entity.minPrice = price == null ? BigDecimal.ZERO : price.getSalePrice();
    entity.maxPrice = price == null ? BigDecimal.ZERO : price.getListPrice();
    entity.availableQty = inventory == null ? 0 : inventory.getAvailableQty();
    entity.stockStatus = inventory != null && inventory.getAvailableQty() > 0 ? "in_stock" : "out_of_stock";
    entity.productStatus = spu.getStatus();
    entity.publishStatus = spu.getPublishStatus();
    entity.auditStatus = spu.getAuditStatus();
    return entity;
}
```

- [ ] **Step 5: Run the projection tests to verify they pass**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.search.api.StorefrontProductControllerTest" --no-daemon`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java
git commit -m "feat: implement storefront projection refresh"
```

## Task 4: Trigger Projection Refresh from Product Writes

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
- Test: `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`

- [ ] **Step 1: Write the failing product-write projection test**

```java
@Test
void product_create_populates_storefront_projection() throws Exception {
    MvcResult createResult = mockMvc.perform(post("/admin/products")
            .header("X-User-Id", "9001")
            .header("X-Role", "PLATFORM_ADMIN")
            .header("X-Merchant-Id", "2001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(productPayload))
        .andExpect(status().isCreated())
        .andReturn();

    long productId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asLong();

    mockMvc.perform(get("/products").param("keyword", "flow-hoodie"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value(productId));
}
```

- [ ] **Step 2: Run the end-to-end test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.e2e.ProductManagementFlowTest" --no-daemon`

Expected: FAIL because product creation does not refresh the projection yet.

- [ ] **Step 3: Refresh the projection after product create, update, and delete**

```java
ProductResponse response = new ProductResponse(saved.getId(), saved.getTitle(), saved.getMerchantId(), saved.getCategoryId());
productSearchProjector.refresh(saved.getId());
return response;
```

```java
spu.updateBasics(request.title(), request.categoryId());
productSearchProjector.refresh(spu.getId());
```

```java
spu.archive();
productSearchProjector.refresh(spu.getId());
```

- [ ] **Step 4: Run the end-to-end test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.e2e.ProductManagementFlowTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java
git commit -m "feat: refresh storefront projection after product writes"
```

## Task 5: Trigger Projection Refresh from Inventory and Pricing Writes

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java`
- Test: `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`

- [ ] **Step 1: Write the failing inventory-and-pricing projection tests**

```java
@Test
void inventory_and_price_changes_refresh_projection_values() throws Exception {
    // create product, adjust inventory, update price
    mockMvc.perform(get("/products").param("keyword", "flow"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].minPrice").value(149.0))
        .andExpect(jsonPath("$.data.items[0].stockStatus").value("in_stock"));
}
```

- [ ] **Step 2: Run the end-to-end test to verify it fails**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.e2e.ProductManagementFlowTest" --no-daemon`

Expected: FAIL because inventory and pricing writes do not refresh the projection yet.

- [ ] **Step 3: Refresh the projection from inventory and pricing services**

```java
private void refreshProjectionBySku(Long skuId) {
    Long productId = productSkuRepository.findById(skuId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"))
        .getSpu().getId();
    productSearchProjector.refresh(productId);
}
```

```java
balance.adjust(delta);
inventoryLedgerRepository.save(...);
refreshProjectionBySku(skuId);
```

```java
writePrice(...);
refreshProjectionBySku(skuId);
```

- [ ] **Step 4: Run the end-to-end test to verify it passes**

Run: `.\gradlew.bat test --tests "com.example.ecommerce.e2e.ProductManagementFlowTest" --no-daemon`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/inventory/application/InventoryService.java backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java backend/src/main/java/com/example/ecommerce/search/application/ProductSearchProjector.java backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java
git commit -m "feat: refresh storefront projection after inventory and pricing writes"
```

## Task 6: Update Documentation and Run Full Verification

**Files:**
- Modify: `backend/README.md`
- Test: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
- Test: `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`

- [ ] **Step 1: Update README with projection notes**

```markdown
## Storefront Search

`GET /products` reads from the `storefront_product_search` projection table.
The projection is refreshed synchronously after product, inventory, and pricing writes.
```

- [ ] **Step 2: Run the full backend suite**

Run: `.\gradlew.bat clean test --no-daemon`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/README.md backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java
git commit -m "docs: describe storefront search projection"
```

## Self-Review

- Spec coverage:
  - projection schema is covered by Task 1
  - projection-backed storefront reads are covered by Task 2
  - synchronous projection refresh is covered by Tasks 3 to 5
  - docs and full verification are covered by Task 6
- Placeholder scan:
  - no `TODO`, `TBD`, or vague “implement later” steps remain
- Type consistency:
  - projection table, entity, repository, and projector names are consistent across all tasks
