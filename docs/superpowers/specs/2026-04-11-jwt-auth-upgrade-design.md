# JWT Auth Upgrade Design

## Context

The current project uses a custom MVC interceptor to read these headers directly from each request:

- `X-User-Id`
- `X-Role`
- `X-Merchant-Id`

Those values are converted into `AuthContext` and then consumed throughout product, pricing, and inventory services.

This approach was fast to bootstrap, but it has clear limits:

- there is no real authentication step
- any client can impersonate any user by changing headers
- Swagger and external clients cannot follow a realistic login flow
- the project already includes security-oriented tests, but not a real security stack

The next practical upgrade is to replace direct identity headers with a lightweight JWT-based authentication flow while preserving the existing role and merchant-scope behavior already implemented in the service layer.

## Goals

- Replace the header-based identity contract with `Authorization: Bearer <token>`
- Add a lightweight login endpoint suitable for local development, demo usage, Swagger testing, and automated tests
- Reuse the existing `AuthContext`, `AuthContextHolder`, and role-based authorization rules
- Keep storefront read endpoints anonymous where appropriate
- Integrate with Spring Security so the authentication boundary follows common Spring Boot patterns

## Non-Goals

- Refresh token support
- Logout and token revocation
- Password reset, email verification, MFA, captcha, or account lifecycle features
- Database-backed user management
- OAuth2 social login or third-party identity providers

## Recommended Approach

Use Spring Security with a custom JWT authentication filter and a lightweight local login endpoint backed by configuration-defined demo accounts.

This approach keeps the implementation small enough for the current project phase while setting up the project on a production-shaped authentication path.

## High-Level Design

### Authentication Model

1. Client calls `POST /auth/login` with username and password
2. Application validates credentials against configured local accounts
3. Application returns a signed JWT access token
4. Client sends that token through the `Authorization` header on protected routes
5. A JWT authentication filter validates the token and restores `AuthContext`

### Authorization Model

Authorization rules remain unchanged from the existing business layer:

- `PLATFORM_ADMIN` can access cross-merchant data
- `MERCHANT_ADMIN` is limited to their own `merchantId`
- storefront read endpoints remain anonymous

The main change is how identity reaches the business layer, not how permissions are evaluated once identity is present.

## Login Flow

### Endpoint

- `POST /auth/login`

### Request Body

```json
{
  "username": "platform-admin",
  "password": "platform-secret"
}
```

### Response Body

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "accessToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 9001,
      "username": "platform-admin",
      "role": "PLATFORM_ADMIN",
      "merchantId": 2001
    }
  }
}
```

### Invalid Login Behavior

Invalid username or password returns:

- HTTP `401 Unauthorized`
- existing API error shape
- stable business error code for bad credentials

No distinction should be made between "unknown user" and "bad password".

## Account Source

### Chosen Model

Use configuration-defined local accounts in `application.yml`.

Suggested structure:

```yaml
app:
  auth:
    jwt:
      issuer: product-management-api
      secret: change-me-for-prod
      access-token-ttl-minutes: 60
    users:
      - username: platform-admin
        password: platform-secret
        user-id: 9001
        role: PLATFORM_ADMIN
        merchant-id: 2001
      - username: merchant-admin
        password: merchant-secret
        user-id: 9002
        role: MERCHANT_ADMIN
        merchant-id: 2001
```

### Rationale

This is the fastest path to a real authentication flow without prematurely designing a user domain model. It is sufficient for:

- local development
- Swagger usage
- integration tests
- demo environments

The login endpoint and JWT boundary can later be reused even if accounts move into a database table.

## JWT Design

### Signing Strategy

Use a symmetric secret with `HS256`.

This is acceptable for the current project phase because:

- there is only one backend service issuing and validating tokens
- setup remains simple
- tests and local development stay easy to configure

### Required Claims

- `sub`: username
- `uid`: internal user ID
- `role`: `PLATFORM_ADMIN` or `MERCHANT_ADMIN`
- `merchantId`
- `iss`: configured issuer
- `iat`
- `exp`

### Token Lifetime

Use a configurable access token TTL measured in minutes. Default recommendation:

- `60` minutes for local development

There is no refresh token in this phase. Clients should log in again after expiration.

### Validation Rules

Token validation must fail when:

- signature is invalid
- token is expired
- issuer does not match
- required claims are missing
- role claim does not map to known `AuthRole`

These cases should all result in `401 Unauthorized`.

## Spring Security Integration

### New Security Boundary

Introduce `spring-boot-starter-security` and replace the current MVC interceptor-driven authentication boundary with a Spring Security filter chain.

### Anonymous Endpoints

The following routes remain anonymous:

- `POST /auth/login`
- `GET /products`
- `/swagger-ui.html`
- `/swagger-ui/**`
- `/v3/api-docs/**`

### Protected Endpoints

Protected routes include:

- `/admin/**`
- `/inventory/reservations/**`

If additional protected paths already rely on `AuthContext`, they should also pass through JWT validation rather than direct header parsing.

### Filter Responsibilities

A custom JWT authentication filter should:

1. Read `Authorization`
2. Ignore requests without a Bearer token on anonymous routes
3. Reject missing or invalid Bearer tokens on protected routes
4. Parse the JWT
5. Convert claims into:
   - Spring Security `Authentication`
   - existing `AuthContext`
6. Populate both `SecurityContextHolder` and `AuthContextHolder`
7. Clear thread-local auth state at request completion

## Existing Auth Code Migration

### Reuse

Keep and reuse:

- `AuthContext`
- `AuthContextHolder`
- `AuthRole`

This avoids a full service-layer rewrite because business methods already depend on these abstractions.

### Replace

Retire the current:

- `AuthInterceptor`
- MVC registration in `WebMvcConfiguration`

Those classes are tightly coupled to the old identity-header model and should not continue to authenticate requests once JWT support is active.

## Login Service Design

Add a small authentication module responsible for:

- loading configured auth properties
- resolving a user by username
- verifying password equality
- signing JWTs
- mapping users to login responses

Expected logical components:

- auth properties model
- login request/response DTOs
- local account provider
- JWT token service
- authentication controller

This keeps auth concerns separate from product, inventory, and pricing modules.

## Password Handling

### Phase Scope

Passwords are stored in configuration and compared directly in this phase.

### Trade-Off

This is not production-grade password storage, but it is acceptable here because:

- the user explicitly chose the lightweight path
- accounts are local bootstrap accounts rather than managed end-user credentials
- the main goal is to establish real token-based authentication, not full identity management

### Forward Path

If the project later introduces a user table, password hashing should be added at that stage without changing the JWT-protected API contract.

## Error Handling

### Unauthenticated

Return `401 Unauthorized` when:

- token is missing on a protected route
- token format is invalid
- token fails validation
- login credentials are invalid

### Unauthorized

Return `403 Forbidden` when:

- token is valid, but the user lacks permission for the action
- merchant scope checks fail after authentication

This preserves the current distinction already used by service-layer business rules.

## Swagger And Developer Experience

Add OpenAPI Bearer authentication metadata so Swagger UI can authorize protected endpoints with a JWT.

The documentation should show:

- login endpoint usage
- default local demo accounts
- how to paste `Bearer <token>` into Swagger's authorize dialog

This makes the security upgrade immediately usable rather than purely internal.

## Testing Strategy

### New Authentication Tests

Cover:

- login succeeds with configured platform account
- login succeeds with configured merchant account
- login fails with bad password
- protected endpoint rejects missing token
- protected endpoint rejects malformed token
- protected endpoint rejects expired token

### Authorization Regression Tests

Preserve and adapt existing coverage so that:

- platform admin JWT still allows cross-merchant operations
- merchant admin JWT still blocks cross-merchant operations
- storefront `GET /products` remains anonymous
- Swagger/OpenAPI endpoints remain anonymous

### Test Utility Strategy

Because many existing tests currently set identity headers directly, add a shared helper strategy for tests to obtain or synthesize JWT tokens consistently.

This may be done through:

- calling the login endpoint in integration tests, or
- generating test tokens through a dedicated test support helper

The preferred direction is to minimize repeated token setup code across test classes.

## Configuration Design

### Application Properties

Add structured properties under `app.auth`.

Suggested groups:

- `app.auth.jwt`
- `app.auth.users`

### Environment Overrides

Support environment-variable overrides for the JWT secret at minimum so local defaults do not become the only deployment path.

Recommended examples:

- `APP_AUTH_JWT_SECRET`
- `APP_AUTH_JWT_ISSUER`
- `APP_AUTH_JWT_ACCESS_TOKEN_TTL_MINUTES`

## Implementation Risks

### Test Migration Size

A large portion of integration tests currently depend on identity headers. The upgrade will require broad but mechanical test updates.

### Thread-Local Cleanup

Because the project already uses `AuthContextHolder`, request completion cleanup remains important. The security filter must always clear thread-local auth state to prevent cross-request leakage in tests.

### Secret Handling

The default JWT secret will exist in local configuration for bootstrap purposes. Documentation must make clear that deployments should override it.

## Success Criteria

- The project authenticates protected requests through JWT Bearer tokens rather than raw identity headers
- A lightweight login endpoint exists and returns usable access tokens
- Existing role and merchant-scope authorization rules still work
- Storefront read endpoints remain anonymously accessible
- Swagger can authorize protected requests with Bearer JWT
- Full backend tests pass after the migration
