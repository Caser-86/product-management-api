# Price Schedule Response DTO Design

- Date: 2026-04-11
- Status: Draft
- Scope: Replace the map-based response for admin price schedule creation with a typed DTO

## 1. Goal

The pricing module still has one remaining admin endpoint that returns a generic
`Map<String, Object>` payload:

- `POST /admin/skus/{skuId}/price-schedules`

The goal of this phase is to:

- keep the existing endpoint path stable
- replace the map-based response with a typed DTO
- preserve current schedule creation behavior, validation, and authorization
- finish the current response-contract unification pass for the main admin APIs

## 2. Recommended Approach

Upgrade the existing endpoint in place:

- `POST /admin/skus/{skuId}/price-schedules`

Return a typed response DTO instead of `Map<String, Object>`.

Why this approach:

- it removes the last obvious generic response in the pricing module
- it improves Swagger output and compile-time safety
- it keeps the scope narrow enough to finish cleanly in one pass

## 3. Alternatives Considered

### 3.1 Recommended: DTO for create-schedule only

Pros:

- smallest safe change
- preserves current API flow
- finishes the contract cleanup quickly

Cons:

- does not enrich `applySchedule` responses

### 3.2 DTO plus richer apply response

Pros:

- more complete schedule workflow contract

Cons:

- expands beyond the current cleanup goal
- risks mixing behavior work with response shaping

### 3.3 Full schedule read/query API

Pros:

- more complete pricing operations surface

Cons:

- clearly broader than a response-contract cleanup

## 4. Scope Boundaries

Included:

- typed DTO for create-schedule responses
- service and controller signature updates
- OpenAPI contract verification
- existing controller-flow regression verification

Excluded:

- schedule query APIs
- changes to `applySchedule`
- schema changes
- new business logic

## 5. DTO Design

Add:

- `PriceScheduleResponse`

Fields:

- `scheduleId`
- `status`

This mirrors the current payload shape exactly, but makes the contract explicit.

## 6. Authorization and Error Model

This phase should preserve the current semantics exactly:

- platform admins can create schedules across merchants
- merchant admins remain constrained to their own merchant scope
- existing validation and error codes remain unchanged

No new error codes should be introduced.

## 7. Application Design

Recommended structure:

- add `PriceScheduleResponse` in `pricing.api`
- update `PricingService.createSchedule(...)` to return that DTO
- update `PricingController.createSchedule(...)` to return
  `ApiResponse<PriceScheduleResponse>`
- extend the OpenAPI documentation test to assert the typed response schema

The business logic for building schedule payload JSON and saving the schedule
should remain unchanged.

## 8. Testing Strategy

### 8.1 Happy-path coverage

- schedule creation returns typed `scheduleId` and `status`
- scheduled price application flow still works after the response upgrade

### 8.2 Contract coverage

- OpenAPI exposes `ApiResponsePriceScheduleResponse` for schedule creation

### 8.3 Regression coverage

- merchant-scope checks continue to work
- existing pricing tests remain green

## 9. Documentation

Update backend documentation to mention that schedule creation now returns a
typed response object with:

- `scheduleId`
- `status`

## 10. Rollout Plan

Suggested implementation order:

1. add failing contract assertions
2. add the response DTO
3. update service and controller signatures
4. update README and run full verification

## 11. Success Criteria

This phase is complete when:

- `POST /admin/skus/{skuId}/price-schedules` returns a typed DTO
- OpenAPI documents the typed contract
- pricing controller tests still pass
- full backend tests pass
