# Auth, Inventory Ledger, and Price Scheduling Design

- Date: 2026-04-10
- Status: Draft
- Scope: Authorization hardening, inventory audit trail, and scheduled price execution

## 1. Goal

This design adds three missing production-oriented capabilities to the current
product management API:

- request-header based authentication and merchant scope enforcement
- durable inventory ledger records for stock-changing operations
- automatic execution of due price schedules

The goal is to harden the existing implementation without replacing the current
project shape, API style, or data model foundations.

## 2. Decisions

### 2.1 Authentication model

Use a lightweight request-header identity model for this phase.

Required request headers for protected endpoints:

- `X-User-Id`
- `X-Role`
- `X-Merchant-Id`

Supported roles:

- `PLATFORM_ADMIN`
- `MERCHANT_ADMIN`

Authorization rules:

- `PLATFORM_ADMIN` may operate across merchants
- `MERCHANT_ADMIN` may only access data owned by its own merchant
- storefront read endpoints remain anonymous
- admin endpoints and high-risk write endpoints require authentication

### 2.2 Protected endpoint scope

Protected:

- `/admin/**`
- inventory reservation and confirmation endpoints
- pricing update and pricing schedule endpoints

Anonymous:

- storefront read endpoints such as `/products`

This keeps the storefront easy to consume while hardening business-critical
mutation paths.

### 2.3 Merchant scope enforcement

Merchant scope must be enforced on the server side.

- incoming request parameters are never trusted for merchant ownership
- services must resolve the effective merchant from the authenticated request
- product, inventory, and pricing operations must verify resource ownership
  before mutation

Admin list behavior:

- `MERCHANT_ADMIN` always uses its own merchant id, even if another merchant id
  is supplied in the request
- `PLATFORM_ADMIN` may query across merchants and may optionally filter by
  merchant id

### 2.4 Inventory audit design

The existing `inventory_balance` table remains the source of truth for current
stock counters.

The existing `inventory_ledger` table becomes the immutable audit trail for
stock-changing operations. Ledger rows must be written for:

- `reserve`
- `confirm`
- `adjust`

Each ledger record stores:

- `skuId`
- `merchantId`
- `bizType`
- `bizId`
- `deltaAvailable`
- `deltaReserved`
- `createdAt`

The system should also expose an admin history endpoint:

- `GET /admin/skus/{skuId}/inventory/history`

### 2.5 Price scheduling design

The current manual apply endpoint remains available:

- `POST /admin/price-schedules/{scheduleId}/apply`

Add a scheduled background runner that scans due `pending` schedules and applies
them automatically.

Rules:

- only schedules with `status = pending` are eligible
- only schedules with `effectiveAt <= now` are eligible
- execution must be idempotent
- repeated scans must not re-apply an already applied schedule

Implementation shape:

- enable Spring scheduling
- add a scheduled component that periodically calls a batch method such as
  `applyDueSchedules(limit)`
- keep the apply logic centralized in `PricingService`

## 3. Architecture

### 3.1 Auth flow

- an MVC interceptor reads request headers
- the interceptor builds a request-scoped auth context
- protected endpoints reject missing or malformed identity headers
- controllers and services read the auth context instead of trusting request
  payload merchant information

### 3.2 Inventory flow

- inventory balance continues to update within the same transaction
- the same transaction writes an `inventory_ledger` row
- the history endpoint reads ledger rows ordered by time descending

### 3.3 Price schedule flow

- admin creates a future price schedule
- scheduled runner scans due schedules
- for each due schedule, pricing service validates and applies the price update
- current price and history are updated
- the schedule transitions from `pending` to `applied`

## 4. API Changes

### 4.1 Header contract

Protected endpoints accept:

- `X-User-Id: 9001`
- `X-Role: PLATFORM_ADMIN`
- `X-Merchant-Id: 2001`

Failure behavior:

- missing or malformed auth headers return `401`
- authenticated but out-of-scope access returns `403`

### 4.2 Product admin behavior changes

- create product:
  - merchant admin creates only for its own merchant
  - platform admin may choose a target merchant
- list products:
  - merchant admin is restricted to its own merchant
  - platform admin may filter or query broadly
- update and delete:
  - resource ownership is validated before mutation

### 4.3 Inventory changes

New endpoint:

- `GET /admin/skus/{skuId}/inventory/history`

Existing protected endpoints:

- `POST /inventory/reservations`
- `POST /inventory/reservations/{reservationId}/confirm`
- `POST /admin/skus/{skuId}/inventory/adjustments`
- `GET /admin/skus/{skuId}/inventory`

### 4.4 Pricing changes

Existing protected endpoints:

- `PATCH /admin/skus/{skuId}/prices`
- `POST /admin/skus/{skuId}/price-schedules`
- `POST /admin/price-schedules/{scheduleId}/apply`
- `GET /admin/skus/{skuId}/price-history`

No public pricing write endpoints are allowed.

## 5. Data Model and Code Impact

### 5.1 New code units

- auth context model
- auth interceptor
- web configuration for interceptor registration
- inventory ledger repository
- inventory history response mapping
- scheduled pricing runner component

### 5.2 Existing code updates

- product services must enforce merchant scope
- inventory service must persist ledger records
- pricing service must expose due-schedule batch execution
- exception mapping must include auth-specific errors

### 5.3 Existing tables reused

- `inventory_ledger`
- `price_schedule`
- `price_history`
- `price_current`
- product and inventory ownership fields already needed for scope checks

No new database tables are required for this phase.

## 6. Error Handling

Add auth-oriented error codes and mappings for:

- missing authentication headers
- invalid role
- merchant scope denied

Expected status mapping:

- `401` for unauthenticated requests
- `403` for authenticated but unauthorized access
- `404` for missing resources
- `409` for business conflicts such as invalid schedule state

## 7. Testing Strategy

### 7.1 Authentication tests

- protected endpoint without headers returns `401`
- merchant admin cannot access another merchant's product or SKU
- platform admin can access multiple merchants
- storefront read endpoint stays anonymous

### 7.2 Inventory tests

- reserve writes an inventory ledger row
- confirm writes an inventory ledger row
- adjust writes an inventory ledger row
- history endpoint returns recorded ledger entries in reverse chronological order

### 7.3 Pricing tests

- due schedule is applied by batch execution
- future schedule is skipped
- already applied schedule is not applied twice
- scheduled execution writes price history as expected

## 8. Out of Scope

This phase does not include:

- JWT or OAuth token issuance
- user persistence and login flows
- storefront customer authentication
- search engine projection implementation
- release inventory endpoint
- distributed scheduler or multi-node locking

## 9. Rollout Order

Recommended implementation order:

1. auth context, interceptor, and scope enforcement
2. inventory ledger persistence and history endpoint
3. scheduled price runner and due-schedule batch execution
4. documentation and test coverage updates

## 10. Success Criteria

This phase is complete when:

- protected endpoints reject unauthenticated access
- merchant admins are constrained to their own merchant data
- platform admins can operate across merchants
- inventory-changing actions create durable ledger history
- due price schedules are automatically executable by the scheduler path
- automated tests cover the new behavior and pass
