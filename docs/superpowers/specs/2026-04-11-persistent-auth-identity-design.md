# Persistent Auth and Identity Design

- Date: 2026-04-11
- Status: Draft
- Scope: Replace configuration-only login accounts with a database-backed
  identity layer while keeping the current JWT model

## 1. Goal

The project currently supports JWT login, but the login source is still a fixed
list of users in `application.yml`.

That was the right lightweight step for local development, but it leaves the
identity layer incomplete:

- credentials are not persisted
- merchants and role assignments are not modeled explicitly
- the system cannot evolve cleanly toward real admin identity management

The goal of this phase is to move authentication onto database-backed users and
role assignments while preserving:

- the current `/auth/login` entry point
- the current JWT shape and downstream auth context
- the existing merchant-scope enforcement model

This phase should remain pragmatic. It does not attempt to build a full user
administration console.

## 2. Recommended Approach

Use database-backed auth users with startup seed data.

This phase should:

- add persistent auth tables
- load a small default identity set through Flyway seed data
- update login to authenticate against the database
- keep JWT issuance and request filtering unchanged at the transport level

Why this approach:

- it replaces the last major auth shortcut with real persisted identities
- it preserves the current API and test shape
- it still keeps local startup easy through seeded users

## 3. Alternatives Considered

### 3.1 Recommended: persistent users plus seed data

- credentials and role assignments live in the database
- Flyway seeds a platform admin and one merchant admin
- login reads from repositories instead of config

Pros:

- real identity persistence
- straightforward migration from current auth model
- local development still works out of the box

Cons:

- passwords still need a hashing strategy in this phase
- no full admin user-management UI yet

### 3.2 Stay on config-only auth

Pros:

- almost no implementation work

Cons:

- leaves the identity layer non-persistent
- blocks future user management and audit maturity

### 3.3 Full user administration module now

Pros:

- more complete long-term auth story

Cons:

- scope is too large for the current phase
- would mix identity persistence with a larger admin UX project

## 4. Scope Boundaries

Included:

- persistent auth users
- persistent merchant ownership reference for auth users
- database-backed login lookup
- password hashing for stored credentials
- startup seed identities for local and test use

Excluded:

- refresh token support
- password reset flows
- user self-service profile endpoints
- admin CRUD APIs for managing auth users
- account lockout, MFA, or audit login history

## 5. Identity Model

Add an explicit auth user table. Suggested table name:

- `auth_user`

Suggested fields:

- `id`
- `username`
- `password_hash`
- `role`
- `merchant_id`
- `status`
- `created_at`
- `updated_at`

Recommended initial statuses:

- `active`
- `disabled`

Rules:

- usernames are unique
- `PLATFORM_ADMIN` may still carry a default merchant id for compatibility with
  the current auth context, but merchant scope logic continues to treat the role
  as cross-merchant
- `MERCHANT_ADMIN` must have a valid merchant id

This phase can keep role storage as a simple enum string because the project
currently only uses:

- `PLATFORM_ADMIN`
- `MERCHANT_ADMIN`

## 6. Password Strategy

Passwords should no longer be stored as plaintext in configuration or the
database.

Recommended approach:

- use Spring Security `PasswordEncoder`
- store BCrypt hashes in `auth_user.password_hash`

Login behavior:

- load user by username
- reject missing or disabled users
- verify the raw password against `password_hash`
- issue the same JWT shape already used by the app

This keeps transport behavior stable while making credentials production-shaped.

## 7. Seed Data Strategy

This phase should preserve easy local startup by seeding a small identity set
through Flyway migration or repeatable seed script.

Recommended seed users:

- `platform-admin`
- `merchant-admin`

Seed behavior:

- users are inserted with BCrypt-hashed passwords
- seed usernames and merchant relationships match the current local README
- the old `app.auth.users` config block is removed after migration

This keeps tests and local usage easy without leaving auth tied to config.

## 8. Login Flow Changes

`POST /auth/login` remains the public login API.

What changes internally:

- replace `LocalAuthUserProvider`
- add repository-backed lookup
- validate user status
- verify password hash
- map database user to the existing JWT claims:
  - `uid`
  - `role`
  - `merchantId`

What does not change:

- request shape
- response shape
- JWT filter contract
- downstream `AuthContext` usage in services

## 9. Code Structure

Suggested new units:

- auth user entity
- auth user repository
- auth identity lookup service
- password encoder configuration

Existing units to update:

- `AuthController`
- `LocalAuthUserProvider` should be removed or replaced
- auth-related tests
- `application.yml` auth config block

The JWT token service and request filter should remain largely unchanged because
they already operate on claims, not config-backed users directly.

## 10. Security Model

Current authorization semantics stay the same:

- `PLATFORM_ADMIN` can operate across merchants
- `MERCHANT_ADMIN` stays restricted to its own merchant data

New login validation semantics:

- unknown username -> invalid credentials
- wrong password -> invalid credentials
- disabled user -> invalid credentials or forbidden login, depending on the
  project’s existing error style

I recommend treating disabled users as invalid credentials in this phase to
avoid leaking account-state details.

## 11. Data Migration and Rollout

Rollout order should be:

1. add `auth_user` table migration
2. add seed user migration with hashed passwords
3. add auth user entity and repository
4. replace login lookup implementation
5. remove config-based `app.auth.users`
6. update tests and README

Backward compatibility:

- JWT verification remains unchanged
- only login data source changes

## 12. Error Handling

Expected auth errors remain:

- invalid credentials
- invalid token
- unauthenticated access
- merchant scope denied

This phase may add:

- disabled user handling

But it should still map to the same external style already used by the API.

## 13. Testing Strategy

### 13.1 Login tests

- seeded platform admin can log in
- seeded merchant admin can log in
- wrong password is rejected
- unknown user is rejected
- disabled user is rejected

### 13.2 JWT regression tests

- protected endpoint still accepts a valid bearer token
- anonymous protected access still returns `401`
- storefront read endpoints remain anonymous

### 13.3 Merchant scope regression tests

- merchant admin remains limited to its merchant
- platform admin remains cross-merchant

### 13.4 Seed data tests

- test environment migrations create the expected auth users
- README credentials match seeded identities

## 14. Documentation

Update backend documentation with:

- the fact that auth users now come from the database
- the seeded local usernames/passwords
- any change to startup assumptions

Remove documentation that implies the login source is configuration-only.

## 15. Success Criteria

This phase is complete when:

- login authenticates against persistent auth users
- passwords are stored as hashes, not plaintext
- seeded users allow local startup and testing
- JWT issuance and downstream auth context still work unchanged
- automated tests cover login, auth regression, and merchant scope behavior
