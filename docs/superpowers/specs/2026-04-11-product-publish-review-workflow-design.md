# Product Publish And Review Workflow Design

## Context

The current product module already stores three workflow-related fields on `product_spu`:

- `status`
- `publish_status`
- `audit_status`

The storefront search projection now depends on these fields, but the project does not yet provide a real publish-and-review workflow. As a result, storefront visibility cannot safely enforce the intended rule of "approved and published products only".

This design adds a minimal but complete workflow on top of the existing model so that:

- merchants can submit and resubmit products for review
- platform admins can approve, reject, publish, and unpublish products
- storefront visibility becomes consistent with product workflow state
- workflow actions are auditable

## Goals

- Add a complete review and publish lifecycle without introducing a separate draft version model
- Preserve the current product management structure and build on existing entities and services
- Make storefront visibility depend on approved and published products only
- Record workflow history for auditability
- Keep the rules simple enough to implement safely in the current project stage

## Non-Goals

- Maintaining separate online and draft versions of the same product
- Partial-field review policies
- Bulk workflow operations
- Asynchronous review pipelines or notifications

## Recommended Approach

Use the existing `product_spu` workflow fields as the source of truth and add explicit workflow actions around them. Avoid introducing a draft-version table in this phase.

This is the smallest change that closes the current functional gap while fitting the existing product, search projection, and admin API structure.

## Workflow Model

### Fields

`product_spu` continues to carry:

- `status`
- `audit_status`
- `publish_status`

Additional metadata fields are added:

- `audit_comment`
- `audit_by`
- `audit_at`
- `submitted_at`
- `published_at`
- `published_by`

### Status Values

`status`

- `draft`
- `active`
- `deleted`

`audit_status`

- `pending`
- `approved`
- `rejected`

`publish_status`

- `unpublished`
- `published`

## State Rules

### New Product

Newly created products start as:

- `status = draft`
- `audit_status = pending`
- `publish_status = unpublished`

They are not storefront-visible.

### Submit For Review

When a merchant submits a product for review:

- product must not be deleted
- caller must be a merchant admin
- product must belong to the caller's merchant
- product remains `draft`
- `audit_status` becomes `pending`
- `publish_status` remains `unpublished`
- `submitted_at` is updated

This action is valid for:

- newly created draft products
- previously rejected products

### Approve

When a platform admin approves a product:

- product must not be deleted
- product must be in reviewable state
- `status` becomes `active`
- `audit_status` becomes `approved`
- `publish_status` remains `unpublished`
- `audit_comment`, `audit_by`, and `audit_at` are updated

Approval alone does not make the product visible on storefront.

### Reject

When a platform admin rejects a product:

- product must not be deleted
- product must be in reviewable state
- `status` becomes `draft`
- `audit_status` becomes `rejected`
- `publish_status` becomes `unpublished`
- `audit_comment`, `audit_by`, and `audit_at` are updated

Rejected products are not storefront-visible.

### Publish

When a platform admin publishes a product:

- product must not be deleted
- product must already be `approved`
- `status` must be `active`
- `publish_status` becomes `published`
- `published_at` and `published_by` are updated

Published products become storefront-visible.

### Unpublish

When a platform admin unpublishes a product:

- product must not be deleted
- product must currently be published
- `status` remains `active`
- `audit_status` remains `approved`
- `publish_status` becomes `unpublished`

Unpublished products are no longer storefront-visible.

### Update After Approved Content

If a merchant updates basic product information after the product has already been approved, including products that were previously published and then unpublished:

- the product is automatically taken offline
- `status` becomes `draft`
- `audit_status` becomes `pending`
- `publish_status` becomes `unpublished`
- previous publish metadata remains historical only

This keeps storefront data aligned with reviewed content and avoids online/draft dual-state complexity.

### Delete

Delete remains a soft-delete action:

- `status = deleted`
- `publish_status = unpublished`

Deleted products are never storefront-visible and cannot re-enter the workflow.

## Permissions

### Merchant Admin

Allowed actions:

- create product
- update product
- view own products
- submit for review
- resubmit after rejection

Not allowed:

- approve
- reject
- publish
- unpublish
- operate on other merchants' products

### Platform Admin

Allowed actions:

- cross-merchant product access
- approve
- reject
- publish
- unpublish
- inspect workflow metadata and history

## API Design

### New Endpoints

- `POST /admin/products/{productId}/submit-for-review`
- `POST /admin/products/{productId}/resubmit-for-review`
- `POST /admin/products/{productId}/approve`
- `POST /admin/products/{productId}/reject`
- `POST /admin/products/{productId}/publish`
- `POST /admin/products/{productId}/unpublish`

### Request Bodies

`submit-for-review`

- no body required in the minimal version

`resubmit-for-review`

- no body required in the minimal version

`approve`

- optional `comment`

`reject`

- required `reason`

`publish`

- no body required

`unpublish`

- optional `reason`

### Response Shape Changes

Extend product detail and product list item responses to include:

- `status`
- `auditStatus`
- `publishStatus`
- `auditComment`
- `submittedAt`
- `auditAt`
- `publishedAt`

This keeps the admin UI/API consumers informed without requiring extra workflow lookup calls.

## Data Model Changes

### `product_spu`

Add columns:

- `audit_comment`
- `audit_by`
- `audit_at`
- `submitted_at`
- `published_at`
- `published_by`

### `product_workflow_history`

Create a workflow history table for auditability.

Suggested columns:

- `id`
- `product_id`
- `action`
- `from_status`
- `to_status`
- `from_audit_status`
- `to_audit_status`
- `from_publish_status`
- `to_publish_status`
- `operator_id`
- `operator_role`
- `comment`
- `created_at`

This table records each state-changing action and supports future admin inspection without overloading `product_spu`.

## Domain And Service Changes

### Product Entity

`ProductSpuEntity` should gain explicit workflow methods rather than scattering state mutation across service logic. Expected methods include:

- `submitForReview(...)`
- `resubmitForReview(...)`
- `approve(...)`
- `reject(...)`
- `publish(...)`
- `unpublish(...)`
- `resetToDraftAfterMutation(...)`

These methods should enforce state transition rules close to the domain model.

### Application Service

`ProductCommandService` should orchestrate:

- merchant scope validation
- role validation
- workflow action execution
- workflow history persistence
- projection refresh after each workflow state change

## Storefront Visibility

After this workflow ships, storefront search should only return products where:

- `status != deleted`
- `audit_status = approved`
- `publish_status = published`

This rule applies to the MySQL storefront search projection as well as storefront-facing search queries.

## Projection Integration

`ProductSearchProjector.refresh(productId)` should continue to rebuild the projection row from product, price, and inventory data, but storefront readers may now safely filter by:

- approved audit status
- published publish status

Workflow actions must trigger projection refresh:

- submit
- resubmit
- approve
- reject
- publish
- unpublish
- update that resets a published product back to draft
- delete

## Error Handling

The API should return business errors for invalid transitions, such as:

- submit deleted product
- resubmit a product that is not rejected
- approve product that is not pending review
- reject product that is not pending review
- publish product that is not approved
- unpublish product that is not currently published
- merchant attempting platform-only actions

Existing business exception patterns and error response shapes should be reused.

## Testing Strategy

### Domain/Application Tests

Cover:

- create product defaults to draft, pending, unpublished
- merchant can submit for review
- rejected product can be resubmitted
- platform can approve pending product
- platform can reject pending product
- unapproved product cannot be published
- approved product can be published
- published product can be unpublished
- approved product update resets it to draft, pending, unpublished
- invalid transitions are rejected
- merchant scope and role boundaries are enforced

### Integration Tests

Cover:

- new workflow endpoints
- workflow metadata in product detail/list responses
- workflow history persisted on state change
- storefront search only returns approved and published products after workflow actions
- published product disappears from storefront after update or unpublish

## Migration Strategy

Add a new migration to:

- extend `product_spu`
- create `product_workflow_history`

Existing rows should remain compatible. For products created before this workflow:

- keep current status values as-is
- do not attempt historical backfill beyond nullable workflow metadata

## Risks And Trade-Offs

### Chosen Trade-Off

This design intentionally prefers a single mutable product record over a separate online/draft version model. That means published products must be taken offline when core content changes.

This is acceptable for the current project phase because it:

- keeps implementation size reasonable
- closes the storefront visibility gap
- keeps workflow rules explicit
- avoids introducing version synchronization complexity too early

### Known Future Expansion Path

If the product team later needs uninterrupted online content during edits, a versioned draft model can be added on top of this workflow in a later phase.

## Success Criteria

- Product workflow actions are exposed through admin APIs
- Permissions align with merchant/platform boundaries
- Workflow metadata is persisted and queryable
- Workflow history is recorded
- Storefront visibility safely depends on approved and published products only
- Published product edits trigger re-review
- Full test suite passes with workflow and storefront visibility rules in place
