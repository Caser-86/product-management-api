# Price Schedule Response DTO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the map-based response for `POST /admin/skus/{skuId}/price-schedules` with a typed DTO while preserving current pricing behavior.

**Architecture:** Keep the endpoint path and business logic unchanged, add a focused response DTO in the pricing API package, and update `PricingService` plus `PricingController` to return that DTO end to end. Use OpenAPI contract assertions and existing pricing controller tests to verify the contract upgrade.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, Springdoc OpenAPI, MockMvc, JUnit 5

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`
  Replace the map-based `createSchedule` response type with a typed DTO.
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
  Replace the map-returning `createSchedule` method with a typed DTO.
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
  Keep the existing schedule flow test and tighten one response assertion if needed.
- `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`
  Add a contract assertion proving the endpoint now exposes the typed response schema.
- `backend/README.md`
  Document the typed price schedule creation response.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleResponse.java`
  Typed response DTO for price schedule creation.

## Task 1: Add failing contract test for typed price schedule responses

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java`

- [ ] **Step 1: Add a failing OpenAPI assertion for create-schedule**

Add:

```java
.andExpect(jsonPath("$.paths['/admin/skus/{skuId}/price-schedules'].post.responses['200'].content['*/*'].schema.$ref")
    .value("#/components/schemas/ApiResponsePriceScheduleResponse"))
```

- [ ] **Step 2: Run the OpenAPI documentation test**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --no-daemon
```

Expected: FAIL because the endpoint still exposes `ApiResponseMapStringObject`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/shared/api/OpenApiDocumentationTest.java
git commit -m "test: cover price schedule response contract"
```

## Task 2: Add the typed price schedule response DTO

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleResponse.java`

- [ ] **Step 1: Create the response DTO**

Create:

```java
public record PriceScheduleResponse(
    Long scheduleId,
    String status
) {
}
```

- [ ] **Step 2: Re-run the OpenAPI documentation test**

Run the same command from Task 1 Step 2.

Expected: tests still fail because the controller/service have not switched to the DTO yet, but compilation succeeds.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/api/PriceScheduleResponse.java
git commit -m "feat: add price schedule response dto"
```

## Task 3: Switch service and controller to typed schedule responses

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java`

- [ ] **Step 1: Update service return type**

Change:

```java
public Map<String, Object> createSchedule(Long skuId, PriceScheduleRequest request)
```

To:

```java
public PriceScheduleResponse createSchedule(Long skuId, PriceScheduleRequest request)
```

- [ ] **Step 2: Replace map construction with DTO construction**

Use:

```java
return new PriceScheduleResponse(schedule.getId(), schedule.getStatus());
```

- [ ] **Step 3: Update the controller signature**

Change:

```java
public ApiResponse<Map<String, Object>> createSchedule(...)
```

To:

```java
public ApiResponse<PriceScheduleResponse> createSchedule(...)
```

- [ ] **Step 4: Run focused tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.api.OpenApiDocumentationTest' --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/application/PricingService.java backend/src/main/java/com/example/ecommerce/pricing/api/PricingController.java
git commit -m "feat: unify price schedule response contract"
```

## Task 4: Document and verify the schedule response upgrade

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README**

Document that `POST /admin/skus/{skuId}/price-schedules` now returns:

- `scheduleId`
- `status`

- [ ] **Step 2: Run full backend verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only price-schedule-response-dto related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe price schedule response dto"
```

## Spec Coverage Check

- Typed DTO replacement for schedule creation response: covered by Tasks 2 and 3.
- OpenAPI contract verification: covered by Task 1 and Task 3.
- Documentation and regression verification: covered by Task 4.

No spec sections are left without an implementation task.
