# Product Publish And Review Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a complete product review and publish workflow so admin APIs can submit, approve, reject, publish, and unpublish products while storefront visibility safely depends on approved and published products only.

**Architecture:** Extend the existing `product_spu` workflow fields instead of adding a parallel draft version model. Keep workflow rules in the product domain entity, orchestrate state transitions and audit persistence in `ProductCommandService`, and refresh the storefront search projection after every workflow mutation.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Flyway, H2/MySQL-compatible SQL, JUnit 5, MockMvc

---

## File Structure

### Create

- `backend/src/main/resources/db/migration/V7__add_product_review_workflow.sql`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryEntity.java`
- `backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryRepository.java`
- `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowActionRequest.java`
- `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowRejectRequest.java`
- `backend/src/test/java/com/example/ecommerce/product/domain/ProductWorkflowStateTest.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`

### Modify

- `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java`
- `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- `backend/src/main/java/com/example/ecommerce/product/api/ProductResponse.java`
- `backend/src/main/java/com/example/ecommerce/product/api/ProductListResponse.java`
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchEntity.java`
- `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`
- `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`
- `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
- `backend/README.md`

## Task 1: Add Workflow Persistence And Domain Rules

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__add_product_review_workflow.sql`
- Create: `backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryEntity.java`
- Create: `backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryRepository.java`
- Create: `backend/src/test/java/com/example/ecommerce/product/domain/ProductWorkflowStateTest.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java`

- [ ] **Step 1: Write the failing domain tests**

```java
@Test
void approve_moves_pending_product_to_active_and_approved() {
    ProductSpuEntity product = ProductSpuEntity.draft(2001L, "SPU-WF-1", "workflow-product", 33L);

    product.submitForReview();
    product.approve(9001L, "looks good");

    assertThat(product.getStatus()).isEqualTo("active");
    assertThat(product.getAuditStatus()).isEqualTo("approved");
    assertThat(product.getPublishStatus()).isEqualTo("unpublished");
    assertThat(product.getAuditComment()).isEqualTo("looks good");
}

@Test
void published_product_update_resets_it_to_pending_unpublished() {
    ProductSpuEntity product = ProductSpuEntity.draft(2001L, "SPU-WF-2", "workflow-product", 33L);

    product.submitForReview();
    product.approve(9001L, "ok");
    product.publish(9001L);
    product.updateBasics("workflow-product-v2", 44L);

    assertThat(product.getStatus()).isEqualTo("draft");
    assertThat(product.getAuditStatus()).isEqualTo("pending");
    assertThat(product.getPublishStatus()).isEqualTo("unpublished");
}
```

- [ ] **Step 2: Run the domain tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.domain.ProductWorkflowStateTest' --no-daemon
```

Expected:

- `FAIL` because workflow methods and metadata getters do not exist yet

- [ ] **Step 3: Add workflow schema migration**

```sql
alter table product_spu
    add column audit_comment varchar(255),
    add column audit_by bigint,
    add column audit_at timestamp null,
    add column submitted_at timestamp null,
    add column published_at timestamp null,
    add column published_by bigint;

create table product_workflow_history (
    id bigint generated by default as identity primary key,
    product_id bigint not null,
    action varchar(64) not null,
    from_status varchar(32) not null,
    to_status varchar(32) not null,
    from_audit_status varchar(32) not null,
    to_audit_status varchar(32) not null,
    from_publish_status varchar(32) not null,
    to_publish_status varchar(32) not null,
    operator_id bigint not null,
    operator_role varchar(64) not null,
    comment varchar(255),
    created_at timestamp not null default current_timestamp
);
```

- [ ] **Step 4: Add workflow history entity and repository**

```java
@Entity
@Table(name = "product_workflow_history")
public class ProductWorkflowHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String action;

    @Column(name = "from_status", nullable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    public static ProductWorkflowHistoryEntity of(
        Long productId,
        String action,
        String fromStatus,
        String toStatus,
        String fromAuditStatus,
        String toAuditStatus,
        String fromPublishStatus,
        String toPublishStatus,
        Long operatorId,
        String operatorRole,
        String comment
    ) { ... }
}
```

```java
public interface ProductWorkflowHistoryRepository extends JpaRepository<ProductWorkflowHistoryEntity, Long> {
}
```

- [ ] **Step 5: Implement minimal workflow rules in `ProductSpuEntity`**

```java
public void submitForReview() {
    if (isDeleted()) {
        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "product not found");
    }
    this.auditStatus = "pending";
    this.publishStatus = "unpublished";
    this.submittedAt = LocalDateTime.now();
}

public void approve(Long operatorId, String comment) {
    if (!"pending".equals(auditStatus)) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product is not pending review");
    }
    this.status = "active";
    this.auditStatus = "approved";
    this.publishStatus = "unpublished";
    this.auditComment = comment;
    this.auditBy = operatorId;
    this.auditAt = LocalDateTime.now();
}

public void publish(Long operatorId) {
    if (!"approved".equals(auditStatus) || !"active".equals(status)) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "product is not publishable");
    }
    this.publishStatus = "published";
    this.publishedBy = operatorId;
    this.publishedAt = LocalDateTime.now();
}
```

- [ ] **Step 6: Re-run the domain tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.domain.ProductWorkflowStateTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit Task 1**

```powershell
git add backend/src/main/resources/db/migration/V7__add_product_review_workflow.sql `
        backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryEntity.java `
        backend/src/main/java/com/example/ecommerce/product/domain/ProductWorkflowHistoryRepository.java `
        backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java `
        backend/src/test/java/com/example/ecommerce/product/domain/ProductWorkflowStateTest.java
git commit -m "feat: add product workflow domain model"
```

## Task 2: Expose Workflow Metadata In Admin Product Reads

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/ProductResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/ProductListResponse.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java`

- [ ] **Step 1: Write the failing API assertions**

```java
mockMvc.perform(get("/admin/products/{productId}", productId)
        .header(USER_ID_HEADER, "9001")
        .header(ROLE_HEADER, "PLATFORM_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.status").value("draft"))
    .andExpect(jsonPath("$.data.auditStatus").value("pending"))
    .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));
```

- [ ] **Step 2: Run the product controller tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductControllerTest' --tests 'com.example.ecommerce.product.api.AdminProductListTest' --no-daemon
```

Expected:

- `FAIL` because the response records do not expose workflow fields yet

- [ ] **Step 3: Expand product response records**

```java
public record ProductResponse(
    Long id,
    String title,
    Long merchantId,
    Long categoryId,
    String status,
    String auditStatus,
    String publishStatus,
    String auditComment,
    LocalDateTime submittedAt,
    LocalDateTime auditAt,
    LocalDateTime publishedAt
) {
}
```

- [ ] **Step 4: Map the new response fields in `ProductCommandService`**

```java
private ProductResponse toResponse(ProductSpuEntity spu) {
    return new ProductResponse(
        spu.getId(),
        spu.getTitle(),
        spu.getMerchantId(),
        spu.getCategoryId(),
        spu.getStatus(),
        spu.getAuditStatus(),
        spu.getPublishStatus(),
        spu.getAuditComment(),
        spu.getSubmittedAt(),
        spu.getAuditAt(),
        spu.getPublishedAt()
    );
}
```

- [ ] **Step 5: Re-run the controller tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductControllerTest' --tests 'com.example.ecommerce.product.api.AdminProductListTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 2**

```powershell
git add backend/src/main/java/com/example/ecommerce/product/api/ProductResponse.java `
        backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductListTest.java
git commit -m "feat: expose product workflow metadata"
```

## Task 3: Add Workflow Action Endpoints And Service Orchestration

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowActionRequest.java`
- Create: `backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowRejectRequest.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java`

- [ ] **Step 1: Write the failing endpoint tests**

```java
mockMvc.perform(post("/admin/products/{productId}/approve", productId)
        .header(USER_ID_HEADER, "9001")
        .header(ROLE_HEADER, "PLATFORM_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"comment":"approved for publish"}
            """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.auditStatus").value("approved"))
    .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));
```

```java
mockMvc.perform(post("/admin/products/{productId}/approve", productId)
        .header(USER_ID_HEADER, "9002")
        .header(ROLE_HEADER, "MERCHANT_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"comment":"should fail"}
            """))
    .andExpect(status().isForbidden());
```

- [ ] **Step 2: Run the workflow controller tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductWorkflowControllerTest' --no-daemon
```

Expected:

- `FAIL` because the workflow routes and service methods do not exist yet

- [ ] **Step 3: Add request DTOs and controller routes**

```java
public record ProductWorkflowActionRequest(String comment) {
}
```

```java
public record ProductWorkflowRejectRequest(
    @NotBlank String reason
) {
}
```

```java
@PostMapping("/{productId}/approve")
public ApiResponse<ProductResponse> approve(
    @PathVariable Long productId,
    @RequestBody(required = false) ProductWorkflowActionRequest request
) {
    return ApiResponse.success(productCommandService.approve(productId, request == null ? null : request.comment()));
}
```

- [ ] **Step 4: Implement orchestration in `ProductCommandService`**

```java
public ProductResponse approve(Long productId, String comment) {
    AuthContext auth = AuthContextHolder.getRequired();
    assertPlatformAdmin(auth);
    ProductSpuEntity spu = loadMutableProduct(productId);
    WorkflowSnapshot before = WorkflowSnapshot.from(spu);
    spu.approve(auth.userId(), comment);
    workflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
        spu.getId(),
        "approve",
        before.status(),
        spu.getStatus(),
        before.auditStatus(),
        spu.getAuditStatus(),
        before.publishStatus(),
        spu.getPublishStatus(),
        auth.userId(),
        auth.role(),
        comment
    ));
    productSearchProjector.refresh(spu.getId());
    return toResponse(spu);
}
```

- [ ] **Step 5: Re-run the workflow controller tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductWorkflowControllerTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 3**

```powershell
git add backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowActionRequest.java `
        backend/src/main/java/com/example/ecommerce/product/api/ProductWorkflowRejectRequest.java `
        backend/src/main/java/com/example/ecommerce/product/api/AdminProductController.java `
        backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductWorkflowControllerTest.java
git commit -m "feat: add admin product workflow endpoints"
```

## Task 4: Enforce Re-Review On Published Product Updates

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java`
- Modify: `backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java`

- [ ] **Step 1: Write the failing regression test**

```java
mockMvc.perform(put("/admin/products/{productId}", productId)
        .header(USER_ID_HEADER, "9002")
        .header(ROLE_HEADER, "MERCHANT_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "title": "updated-after-publish",
              "categoryId": 66
            }
            """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.status").value("draft"))
    .andExpect(jsonPath("$.data.auditStatus").value("pending"))
    .andExpect(jsonPath("$.data.publishStatus").value("unpublished"));
```

- [ ] **Step 2: Run the targeted controller test and verify it fails**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductControllerTest.updating_published_product_resets_review_workflow' --no-daemon
```

Expected:

- `FAIL` because updates currently only change title and category

- [ ] **Step 3: Implement reset-to-draft behavior**

```java
public void updateBasics(String title, Long categoryId) {
    this.title = title;
    this.categoryId = categoryId;
    if ("published".equals(this.publishStatus)) {
        resetToDraftAfterMutation();
    }
}

public void resetToDraftAfterMutation() {
    this.status = "draft";
    this.auditStatus = "pending";
    this.publishStatus = "unpublished";
}
```

- [ ] **Step 4: Persist workflow history for post-publish mutation**

```java
if ("published".equals(before.publishStatus()) &&
    !"published".equals(spu.getPublishStatus())) {
    workflowHistoryRepository.save(ProductWorkflowHistoryEntity.of(
        spu.getId(),
        "update_reset",
        before.status(),
        spu.getStatus(),
        before.auditStatus(),
        spu.getAuditStatus(),
        before.publishStatus(),
        spu.getPublishStatus(),
        auth.userId(),
        auth.role(),
        "product updated after publish"
    ));
}
```

- [ ] **Step 5: Re-run the targeted regression test**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.product.api.AdminProductControllerTest.updating_published_product_resets_review_workflow' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 4**

```powershell
git add backend/src/main/java/com/example/ecommerce/product/domain/ProductSpuEntity.java `
        backend/src/main/java/com/example/ecommerce/product/application/ProductCommandService.java `
        backend/src/test/java/com/example/ecommerce/product/api/AdminProductControllerTest.java
git commit -m "feat: require re-review after published product updates"
```

## Task 5: Enforce Storefront Visibility Rules Through Projection Reads

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java`
- Modify: `backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java`
- Modify: `backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java`

- [ ] **Step 1: Write the failing storefront tests**

```java
storefrontProductSearchRepository.save(StorefrontProductSearchEntity.of(
    1001L, 2001L, 33L, "not-published", 20001L,
    new BigDecimal("129.00"), new BigDecimal("199.00"), 8,
    "in_stock", "active", "unpublished", "approved"
));

mockMvc.perform(get("/products").param("keyword", "not-published"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.total").value(0));
```

```java
mockMvc.perform(get("/products").param("keyword", "flow"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.items[0].title").value("flow-hoodie"));
```

The end-to-end flow must first submit, approve, and publish before this storefront assertion can pass.

- [ ] **Step 2: Run storefront and flow tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --tests 'com.example.ecommerce.e2e.ProductManagementFlowTest' --no-daemon
```

Expected:

- `FAIL` because storefront search still only excludes deleted products and the flow test does not publish the product yet

- [ ] **Step 3: Tighten repository filters and storefront query**

```java
Page<StorefrontProductSearchEntity> findByProductStatusNotAndPublishStatusAndAuditStatus(
    String productStatus,
    String publishStatus,
    String auditStatus,
    Pageable pageable
);
```

```java
return storefrontProductSearchRepository.findByProductStatusNotAndPublishStatusAndAuditStatus(
    "deleted",
    "published",
    "approved",
    pageable
);
```

- [ ] **Step 4: Update the end-to-end flow to execute the workflow**

```java
mockMvc.perform(post("/admin/products/{productId}/submit-for-review", productId)
        .header(USER_ID_HEADER, "9002")
        .header(ROLE_HEADER, "MERCHANT_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001"))
    .andExpect(status().isOk());

mockMvc.perform(post("/admin/products/{productId}/approve", productId)
        .header(USER_ID_HEADER, "9001")
        .header(ROLE_HEADER, "PLATFORM_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"comment":"approved"}"""))
    .andExpect(status().isOk());

mockMvc.perform(post("/admin/products/{productId}/publish", productId)
        .header(USER_ID_HEADER, "9001")
        .header(ROLE_HEADER, "PLATFORM_ADMIN")
        .header(MERCHANT_ID_HEADER, "2001"))
    .andExpect(status().isOk());
```

- [ ] **Step 5: Re-run storefront and end-to-end tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.search.api.StorefrontProductControllerTest' --tests 'com.example.ecommerce.e2e.ProductManagementFlowTest' --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit Task 5**

```powershell
git add backend/src/main/java/com/example/ecommerce/search/domain/StorefrontProductSearchRepository.java `
        backend/src/main/java/com/example/ecommerce/search/application/StorefrontSearchService.java `
        backend/src/test/java/com/example/ecommerce/search/api/StorefrontProductControllerTest.java `
        backend/src/test/java/com/example/ecommerce/e2e/ProductManagementFlowTest.java
git commit -m "feat: enforce storefront visibility workflow rules"
```

## Task 6: Full Regression And Documentation

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update the backend README**

```md
## Product Workflow

Product visibility now follows this lifecycle:

1. Merchant creates product
2. Merchant submits for review
3. Platform admin approves or rejects
4. Platform admin publishes or unpublishes

Storefront search only returns products that are both `approved` and `published`.
```

- [ ] **Step 2: Run the full backend test suite**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect git diff for accidental changes**

Run:

```powershell
git status --short
git diff --stat
```

Expected:

- only workflow-related source, tests, migration, and README changes are present

- [ ] **Step 4: Commit Task 6**

```powershell
git add backend/README.md
git commit -m "docs: describe product review publish workflow"
```

## Spec Coverage Check

- Workflow state machine: covered by Task 1 and Task 4
- Admin workflow endpoints: covered by Task 3
- Response shape changes: covered by Task 2
- Workflow history persistence: covered by Task 1 and Task 3
- Storefront visibility rules: covered by Task 5
- Published product edit reset behavior: covered by Task 4
- Permissions and invalid transitions: covered by Task 1 and Task 3
- Full verification and docs: covered by Task 6

## Placeholder Scan

- No `TODO`, `TBD`, or deferred implementation markers remain
- Each task includes concrete file paths, code snippets, commands, and expected outcomes

## Type Consistency Check

- Workflow DTO names are used consistently as `ProductWorkflowActionRequest` and `ProductWorkflowRejectRequest`
- The domain workflow methods are consistently named `submitForReview`, `resubmitForReview`, `approve`, `reject`, `publish`, `unpublish`, and `resetToDraftAfterMutation`
- Storefront query filters use `productStatus`, `publishStatus`, and `auditStatus` consistently with the spec
