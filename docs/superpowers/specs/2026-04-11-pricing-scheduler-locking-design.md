# Pricing Scheduler Locking Design

- Date: 2026-04-11
- Status: Draft
- Scope: Multi-instance-safe execution for scheduled price application

## 1. Goal

This design hardens scheduled price execution for multi-instance deployment.

The project already supports:

- manual scheduled-price creation
- manual schedule application
- in-process periodic execution through `@Scheduled`

What it does not yet guarantee is single-runner behavior when multiple
application instances are running at the same time. In that situation, multiple
nodes can scan the same pending schedules and attempt to apply them
concurrently.

The goal of this phase is to:

- ensure only one node executes the due-price scan at a time
- keep the current API and price schedule data model intact
- stay within the existing Spring Boot + MySQL deployment shape
- add automated regression coverage for lock-backed scheduled execution

## 2. Recommended Approach

Use ShedLock with the shared MySQL datasource and a dedicated lock table.

Why this approach:

- it is purpose-built for Spring scheduled task locking
- it keeps the current `@Scheduled` runner model
- it avoids inventing custom lock semantics in application code
- it is easy to reason about operationally because the lock state is stored in
  the database the app already depends on

## 3. Alternatives Considered

### 3.1 Recommended: ShedLock backed by MySQL

- add ShedLock dependencies
- create a `shedlock` table through Flyway
- annotate the existing scheduled runner with a named lock

Pros:

- smallest reliable change
- widely used Spring integration
- clear lock timeouts and failure behavior

Cons:

- adds one dependency and one infrastructure table

### 3.2 Custom database claim logic

- write application-managed lock rows or claim columns manually

Pros:

- no third-party dependency

Cons:

- easy to get edge cases wrong
- more code to maintain
- duplicates a solved problem

### 3.3 External scheduler or queue worker

- move due-schedule execution to a dedicated worker or job platform

Pros:

- strongest long-term separation of concerns

Cons:

- much heavier than the project currently needs
- changes deployment and operations significantly

## 4. Scope Boundaries

Included:

- distributed lock protection for due-price schedule scans
- lock table migration
- Spring configuration for lock provider
- tests that verify the runner executes within a lock-backed boundary
- documentation for the new operational assumption

Excluded:

- replacing `@Scheduled` with an external job platform
- per-schedule row claiming or queue-style processing
- changes to price schedule API contracts
- admin UIs or endpoints for lock inspection
- retry orchestration beyond current periodic scanning

## 5. Locking Model

The existing scheduled runner remains the entry point:

- `PriceScheduleRunner.runDueSchedules()`

This method will be protected by a distributed lock with a stable name such as:

- `pricing.due-schedule-runner`

Behavior:

- when one node holds the lock, other nodes skip execution for that interval
- skipped nodes do not fail the application
- the next scheduler tick can try again

Recommended timing:

- `lockAtMostFor`: slightly longer than the expected maximum batch duration
- `lockAtLeastFor`: small buffer to reduce back-to-back duplicate triggers

Reasonable starting values for this project:

- `lockAtMostFor = 1m`
- `lockAtLeastFor = 5s`

These should be externalized to configuration properties so they can be tuned
without code changes later.

## 6. Data Model

Add a Flyway migration for ShedLock's table.

Recommended table name:

- `shedlock`

Recommended columns:

- `name`
- `lock_until`
- `locked_at`
- `locked_by`

This follows ShedLock's JDBC provider conventions and keeps lock state separate
from business tables such as `price_schedule`.

No changes are required to the `price_schedule` table itself for this phase.

## 7. Application Design

### 7.1 Configuration

Add a Spring configuration class that:

- enables ShedLock scheduling support
- provides a JDBC lock provider backed by the app datasource

The configuration should use the shared datasource already configured for JPA
and Flyway.

### 7.2 Scheduled runner

Keep `PriceScheduleRunner` focused on orchestration:

- the scheduler triggers periodically
- the lock ensures only one node runs the batch
- the runner delegates business work to `PricingService.applyDueSchedules(limit)`

The runner should not implement manual SQL locking itself.

### 7.3 Pricing service behavior

`PricingService.applyDueSchedules(limit)` remains the batch worker and should
continue to:

- load pending schedules due at or before `now`
- apply them in order
- mark them applied

This phase should not change manual application semantics or the schedule
payload model.

## 8. Failure and Recovery Semantics

### 8.1 Runner crash while holding the lock

If the node crashes during execution:

- the lock expires after `lockAtMostFor`
- another node can acquire the lock on a later tick

This keeps recovery automatic without manual intervention for normal failures.

### 8.2 Batch exceptions

If `PricingService.applyDueSchedules(limit)` throws:

- the current scheduler run fails
- the lock is released according to ShedLock behavior
- the next scheduler tick can retry remaining pending schedules

This phase does not add dead-letter or retry counters.

### 8.3 Duplicate safety

The lock reduces cross-node duplicate execution of the scan.
The existing service-layer rule that only pending schedules are applied remains
an important second layer of safety.

## 9. Configuration Model

Add configuration properties for scheduler locking, for example:

- `pricing.schedule.lock-name`
- `pricing.schedule.lock-at-most-for`
- `pricing.schedule.lock-at-least-for`

Defaults should remain local-development friendly and require no extra setup
beyond the migration table.

## 10. Testing Strategy

### 10.1 Configuration tests

- application context loads with ShedLock configuration enabled
- lock provider bean is created successfully

### 10.2 Runner lock tests

- scheduled runner executes through a lock-backed path
- runner still delegates to `PricingService.applyDueSchedules(limit)`

Recommended tactic:

- verify the runner bean is proxied and can execute under lock assertions
- keep the business test surface small and focused on lock integration rather
  than re-testing all pricing rules

### 10.3 Pricing regression tests

- due schedules still get applied successfully
- already applied schedules are not applied again
- manual application behavior remains unchanged

## 11. Documentation and Operations

Update backend documentation to note:

- scheduled pricing is now protected for multi-instance deployment
- the lock is stored in the `shedlock` table
- lock timing is configurable

This gives operators a clear explanation when they inspect the database or tune
schedule behavior.

## 12. Rollout Plan

Suggested implementation order:

1. add ShedLock dependencies and Flyway migration
2. add lock configuration and scheduler properties
3. protect `PriceScheduleRunner` with a distributed lock
4. add focused lock integration tests
5. run full backend verification and update README

## 13. Success Criteria

This phase is complete when:

- the application creates and uses a shared lock table for scheduled work
- `PriceScheduleRunner` is protected by a distributed lock
- multi-instance deployments will not run the due-price scan concurrently under
  normal conditions
- existing price scheduling behavior continues to pass automated tests
- backend documentation reflects the new locking model
