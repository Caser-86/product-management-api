# Admin Product List Filter and Sort Design

- Date: 2026-04-11
- Status: Draft
- Scope: Add workflow-oriented filtering and sorting to the admin product list API

## 1. Goal

This design improves the admin-side product list so it is useful for everyday
merchant and platform operations.

The project already supports:

- admin product listing by merchant scope
- pagination
- product workflow fields in each list item

What it does not yet support is:

- filtering by product workflow state
- finding products by title keyword
- explicit sorting for admin review queues

The goal of this phase is to add those missing controls while keeping the
current response shape intact.

## 2. Recommended Approach

Extend `GET /admin/products` with optional workflow filters, keyword search,
and a small set of explicit sort values.

Why this approach:

- it materially improves operator usability
- it does not require schema changes
- it keeps the admin list contract simple and backward compatible

## 3. Alternatives Considered

### 3.1 Recommended: workflow filters plus limited sorting

- add `status`
- add `auditStatus`
- add `publishStatus`
- add `keyword`
- add `sort`

Pros:

- directly supports review, publish, and catalog operations
- low implementation risk
- clean fit for the current domain model

Cons:

- does not yet add date-range or category filtering

### 3.2 Lighter: workflow filters only

Pros:

- smallest change

Cons:

- still awkward for large merchant catalogs because title search and ordering
  remain weak

### 3.3 Heavier: rich admin query model

- add date ranges
- add category filtering
- add operator/auditor filters

Pros:

- closer to a full admin console search experience

Cons:

- broader than the current phase needs

## 4. Scope Boundaries

Included:

- optional filtering by `status`
- optional filtering by `auditStatus`
- optional filtering by `publishStatus`
- optional title keyword search
- explicit admin sort options
- merchant-scope enforcement preserved
- regression coverage for platform and merchant roles

Excluded:

- response shape changes
- category or date-range filters
- workflow history joins
- CSV export or bulk operations

## 5. API Design

Keep the existing endpoint:

- `GET /admin/products`

Existing parameters:

- `merchantId`
- `page`
- `pageSize`

Add optional parameters:

- `status`
- `auditStatus`
- `publishStatus`
- `keyword`
- `sort`

### 5.1 Workflow filter semantics

Supported values should match the persisted workflow fields.

`status`:

- `draft`
- `active`
- `deleted` should remain excluded from normal list results and not be exposed
  as a supported filter in this phase

`auditStatus`:

- `pending`
- `approved`
- `rejected`

`publishStatus`:

- `published`
- `unpublished`

Unknown values should return a validation error.

### 5.2 Keyword semantics

`keyword` matches product title using case-insensitive partial matching.

This is intentionally limited to title search for now.

### 5.3 Sort values

Recommended supported values:

- `created_desc`
- `title_asc`
- `title_desc`

Default:

- `created_desc`

Because the table does not expose a dedicated created timestamp in the current
entity, this phase should treat `id desc` as the effective newest-first order.

Stable tie-breaking should always include:

- `id desc`

## 6. Security and Scope Rules

Current merchant-scope enforcement remains unchanged:

- merchant admins are restricted to their own merchant
- platform admins may query across merchants

Additional filter parameters must never allow a merchant admin to escape their
effective merchant scope.

## 7. Query Design

The current repository methods are too limited for the new combination of
filters and sorts.

Recommended approach:

- add a custom repository query path for admin product listing
- compose predicates dynamically
- keep the default exclusion of deleted products
- apply sorting dynamically from a small enum

This avoids a combinatorial explosion of derived-query methods.

## 8. Validation Rules

Recommended behavior:

- `page` and `pageSize` keep the current forgiving lower-bound clamp
- optional workflow filters must parse to supported enum-like values
- unknown sort values return a validation error
- blank keyword behaves like absent keyword

This phase does not add a page-size maximum to admin queries yet.

## 9. Testing Strategy

### 9.1 Merchant-scope regression

- merchant admin can filter within own merchant data
- merchant admin still cannot query another merchant's data even when passing
  filters

### 9.2 Platform-admin coverage

- platform admin can filter across merchants
- workflow filters combine correctly

### 9.3 Sorting coverage

- title sorting behaves as expected
- default sort remains newest-first by effective creation order

### 9.4 Validation coverage

- invalid workflow filter returns bad request
- invalid sort returns bad request

## 10. Documentation

Update backend documentation to mention the new admin query parameters and the
supported sort values.

## 11. Rollout Plan

Suggested implementation order:

1. extend admin product list tests with workflow filters and sort cases
2. add a custom repository and sort parsing path
3. wire the controller and service parameters
4. update README and run full verification

## 12. Success Criteria

This phase is complete when:

- admin users can filter product lists by workflow state
- admin users can search by product title keyword
- admin users can choose supported sort orders
- merchant-scope rules still hold
- full backend tests pass
