# Product Workflow History Query Design

- Date: 2026-04-11
- Status: Draft
- Scope: Add an admin API for querying product workflow history

## 1. Goal

This design completes the admin-side product workflow loop by exposing the
audit trail already being recorded in `product_workflow_history`.

The project already supports:

- submit, resubmit, approve, reject, publish, and unpublish actions
- workflow-history persistence for those actions
- product detail and list endpoints that expose current workflow state

What it does not yet support is a direct API for reading that workflow history.

The goal of this phase is to:

- add a dedicated admin endpoint for product workflow history
- preserve current product detail/list response shapes
- enforce the same merchant-scope and platform-admin access rules used
  elsewhere in the product module

## 2. Recommended Approach

Add a dedicated endpoint:

- `GET /admin/products/{productId}/workflow-history`

Why this approach:

- it keeps the existing product detail response stable
- it is easy to evolve later into paginated history if needed
- it maps directly to the existing `product_workflow_history` table

## 3. Alternatives Considered

### 3.1 Recommended: separate workflow-history endpoint

Pros:

- clear responsibility
- no change to current product detail contract
- straightforward to paginate later

Cons:

- one more endpoint on the admin surface

### 3.2 Inline history inside product detail

Pros:

- fewer endpoints

Cons:

- makes product detail heavier
- awkward to paginate or filter later

### 3.3 Full history query model with paging and filters

Pros:

- most complete long-term shape

Cons:

- broader than the current need

## 4. Scope Boundaries

Included:

- workflow-history read endpoint
- response DTO for workflow history entries
- merchant-scope and platform-admin permission enforcement
- empty-history handling
- end-to-end test coverage

Excluded:

- paging or filtering for history
- response changes to `ProductResponse`
- workflow-history writes beyond what already exists
- separate admin UI or export flows

## 5. API Design

Add:

- `GET /admin/products/{productId}/workflow-history`

Response should return:

- `items`

Each item should include:

- `action`
- `fromStatus`
- `toStatus`
- `fromAuditStatus`
- `toAuditStatus`
- `fromPublishStatus`
- `toPublishStatus`
- `operatorId`
- `operatorRole`
- `comment`
- `createdAt`

The order should be newest first:

- `createdAt desc`
- `id desc`

## 6. Permission Model

Platform admin:

- may query any product's workflow history

Merchant admin:

- may query only products belonging to their own merchant

Permission failure rules:

- if a merchant admin queries another merchant's product, return
  `PRODUCT_NOT_FOUND`
- if the product truly does not exist, also return `PRODUCT_NOT_FOUND`

This keeps behavior aligned with existing merchant-side workflow endpoints.

## 7. Deleted Product Semantics

Recommended behavior:

- allow authorized users to read workflow history for deleted products

Reasoning:

- workflow history is an audit trail
- deletion should not erase the ability to inspect past workflow actions

This is intentionally different from standard product detail lookup, which
currently hides deleted products.

## 8. Application Design

The existing `ProductCommandService` already owns admin-side product queries and
workflow actions. This phase can extend it with a read method such as:

- `workflowHistory(Long productId)`

Flow:

1. load the product by `productId`
2. enforce merchant scope or platform-admin access
3. load history rows via
   `ProductWorkflowHistoryRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId)`
4. map rows into API DTOs

No schema change is required.

## 9. Response Model

Use dedicated API DTOs instead of raw maps.

Suggested shape:

- `ProductWorkflowHistoryResponse`
- nested `Item`

This keeps Swagger output and test assertions clearer than `Map<String, Object>`
responses.

## 10. Testing Strategy

### 10.1 Happy-path coverage

- platform admin can read workflow history
- merchant admin can read history for own product
- empty history returns an empty `items` array

### 10.2 Permission coverage

- merchant admin cannot read another merchant's product history
- non-existent product returns `PRODUCT_NOT_FOUND`

### 10.3 Ordering coverage

- returned history is newest first by `createdAt desc, id desc`

## 11. Documentation

Update backend documentation to mention:

- the new workflow-history endpoint
- who can access it
- what it returns

## 12. Rollout Plan

Suggested implementation order:

1. add failing controller tests for history reads
2. add response DTOs and service mapping
3. expose the controller endpoint
4. update README and run full verification

## 13. Success Criteria

This phase is complete when:

- admin users can query workflow history through a dedicated endpoint
- merchant-scope restrictions still hold
- deleted products remain auditable through the history endpoint
- full backend tests pass
