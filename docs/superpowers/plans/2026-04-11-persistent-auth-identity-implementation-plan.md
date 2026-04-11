# Persistent Auth and Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move login from configuration-backed demo users to persistent database-backed auth users with hashed passwords while preserving the current JWT behavior.

**Architecture:** Introduce a dedicated `auth_user` persistence model, seed a small default user set through Flyway, and replace `LocalAuthUserProvider` with repository-backed identity lookup. JWT issuance, request filtering, and downstream authorization continue to rely on the same claims and `AuthContext`.

**Tech Stack:** Spring Boot 3, Spring Security, Spring Data JPA, Flyway, BCrypt password hashing, MySQL, JUnit 5, MockMvc

---

## File Map

### Existing files to modify

- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java`
  Replace config-backed login lookup with repository-backed lookup.
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java`
  Remove config-backed user list after migration, keep JWT settings.
- `backend/src/main/resources/application.yml`
  Remove `app.auth.users` config and keep seeded-user-compatible auth settings.
- `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`
  Update login tests to rely on seeded DB users.
- `backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java`
  Keep JWT regression coverage stable against repository-backed login changes.
- `backend/README.md`
  Update auth documentation to describe seeded DB users instead of config users.

### New production files to create

- `backend/src/main/resources/db/migration/V7__create_auth_user_table.sql`
  Create persistent auth user storage.
- `backend/src/main/resources/db/migration/V8__seed_auth_users.sql`
  Seed default local/test users with password hashes.
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserEntity.java`
  JPA entity for persisted auth users.
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserRepository.java`
  Repository for username-based login lookup.
- `backend/src/main/java/com/example/ecommerce/shared/auth/AuthIdentityService.java`
  Map persisted users into the existing JWT login model.
- `backend/src/main/java/com/example/ecommerce/shared/config/PasswordConfiguration.java`
  Expose `PasswordEncoder`.

## Task 1: Add persistent auth schema and seed users

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_auth_user_table.sql`
- Create: `backend/src/main/resources/db/migration/V8__seed_auth_users.sql`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`

- [ ] **Step 1: Write a failing seeded-login test**

Add or update a login test:

```java
@Test
void seeded_platform_admin_can_log_in() throws Exception {
    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "platform-admin",
                  "password": "platform-secret"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.username").value("platform-admin"))
        .andExpect(jsonPath("$.data.user.role").value("PLATFORM_ADMIN"));
}
```

- [ ] **Step 2: Run the login test to verify the current state**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest.seeded_platform_admin_can_log_in' --no-daemon
```

Expected: PASS before migration because config-backed users still exist. This is a baseline safety check before replacing the login source.

- [ ] **Step 3: Add auth schema migration**

Create `V7__create_auth_user_table.sql`:

```sql
create table auth_user (
    id bigint auto_increment primary key,
    username varchar(100) not null unique,
    password_hash varchar(255) not null,
    role varchar(50) not null,
    merchant_id bigint not null,
    status varchar(20) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);
```

- [ ] **Step 4: Add seeded users migration**

Create `V8__seed_auth_users.sql` with BCrypt hashes for:

- `platform-admin / platform-secret`
- `merchant-admin / merchant-secret`

Example insert shape:

```sql
insert into auth_user (username, password_hash, role, merchant_id, status)
values
  ('platform-admin', '{bcrypt-hash}', 'PLATFORM_ADMIN', 2001, 'active'),
  ('merchant-admin', '{bcrypt-hash}', 'MERCHANT_ADMIN', 2001, 'active');
```

- [ ] **Step 5: Re-run the seeded login test**

Run the same command from Step 2.

Expected: still PASS while config-backed auth is still in place, proving migrations do not break bootstrap.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V7__create_auth_user_table.sql backend/src/main/resources/db/migration/V8__seed_auth_users.sql backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java
git commit -m "feat: add persistent auth schema and seed users"
```

## Task 2: Add persistent auth entity, repository, and password support

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserEntity.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserRepository.java`
- Create: `backend/src/main/java/com/example/ecommerce/shared/config/PasswordConfiguration.java`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing tests for disabled and invalid users**

Add tests like:

```java
@Test
void disabled_user_is_rejected() throws Exception {
    disableUser("merchant-admin");

    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "merchant-admin",
                  "password": "merchant-secret"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
}
```

- [ ] **Step 2: Run the disabled-user test to verify it fails**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest.disabled_user_is_rejected' --no-daemon
```

Expected: FAIL because persistent auth repository and disable handling do not yet exist.

- [ ] **Step 3: Add the auth entity and repository**

Create `AuthUserEntity.java` with fields:

```java
@Entity
@Table(name = "auth_user")
public class AuthUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false)
    private String status;

    public boolean isActive() {
        return "active".equals(status);
    }
}
```

Create `AuthUserRepository.java`:

```java
public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {
    Optional<AuthUserEntity> findByUsername(String username);
}
```

- [ ] **Step 4: Add password encoder configuration**

Create `PasswordConfiguration.java`:

```java
@Configuration
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 5: Re-run the disabled-user test**

Run the same command from Step 2.

Expected: FAIL later in login flow because the controller still uses config-backed provider.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserEntity.java backend/src/main/java/com/example/ecommerce/shared/auth/AuthUserRepository.java backend/src/main/java/com/example/ecommerce/shared/config/PasswordConfiguration.java backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java
git commit -m "feat: add persistent auth user model"
```

## Task 3: Replace config-backed login with repository-backed identity lookup

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthIdentityService.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java`
- Test: `backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing repository-backed login tests**

Add tests for:

- seeded platform admin login still works
- wrong password is rejected
- unknown username is rejected

Example:

```java
@Test
void wrong_password_is_rejected() throws Exception {
    mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "platform-admin",
                  "password": "wrong-password"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
}
```

- [ ] **Step 2: Run the login test batch**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.AuthControllerTest' --no-daemon
```

Expected: FAIL after config removal work begins unless the repository-backed service is fully wired.

- [ ] **Step 3: Implement repository-backed identity lookup**

Create `AuthIdentityService.java`:

```java
@Service
public class AuthIdentityService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthIdentityService(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LocalAuthUser authenticate(String username, String password) {
        AuthUserEntity user = authUserRepository.findByUsername(username)
            .filter(AuthUserEntity::isActive)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "invalid credentials");
        }

        return new LocalAuthUser(
            user.getUsername(),
            user.getPasswordHash(),
            user.getId(),
            AuthRole.parse(user.getRole()),
            user.getMerchantId()
        );
    }
}
```

Update `AuthController.java` to depend on `AuthIdentityService` instead of `LocalAuthUserProvider`.

Update or delete `LocalAuthUserProvider.java`; if retained temporarily, make it a thin compatibility wrapper around `AuthIdentityService` only if needed by tests. Otherwise remove it.

- [ ] **Step 4: Re-run the login test batch**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/auth/AuthIdentityService.java backend/src/main/java/com/example/ecommerce/shared/auth/AuthController.java backend/src/main/java/com/example/ecommerce/shared/auth/LocalAuthUserProvider.java backend/src/test/java/com/example/ecommerce/shared/auth/AuthControllerTest.java
git commit -m "feat: switch login to persistent auth users"
```

## Task 4: Remove config-backed users and update JWT regression coverage

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java`
- Modify: `backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java`

- [ ] **Step 1: Write failing config-removal regression checks**

Add regression coverage that still expects:

- valid bearer tokens to authorize admin routes
- anonymous storefront access to remain open
- auth tests not to rely on removed `app.auth.users`

- [ ] **Step 2: Run JWT regression tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.shared.auth.JwtAuthenticationFilterTest' --tests 'com.example.ecommerce.shared.auth.AuthControllerTest' --no-daemon
```

Expected: FAIL while config-backed users are still referenced.

- [ ] **Step 3: Remove config-backed user list**

Update `AuthProperties.java` to keep only JWT settings.

Remove the `app.auth.users` block from `application.yml`, leaving:

```yaml
app:
  auth:
    jwt:
      issuer: ${APP_AUTH_JWT_ISSUER:product-management-api}
      secret: ${APP_AUTH_JWT_SECRET:change-me-for-local-development-only}
      access-token-ttl-minutes: ${APP_AUTH_JWT_ACCESS_TOKEN_TTL_MINUTES:60}
```

Adjust `AuthTestTokens` and any JWT tests so they no longer depend on config-backed users and instead use JWT settings only.

- [ ] **Step 4: Re-run the JWT regression tests**

Run the same command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/auth/AuthProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/example/ecommerce/shared/auth/JwtAuthenticationFilterTest.java backend/src/test/java/com/example/ecommerce/support/AuthTestTokens.java
git commit -m "refactor: remove config-backed auth users"
```

## Task 5: Update documentation and run full verification

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README for persistent auth**

Document that:

- login users now come from seeded DB data
- default local users are seeded by Flyway
- passwords are stored as hashes
- `/auth/login` behavior remains unchanged for API consumers

Add a section like:

```md
## Auth Identities

Login accounts are stored in the database and seeded during migration.

Default local users:

- `platform-admin / platform-secret`
- `merchant-admin / merchant-secret`

The login API remains `POST /auth/login`, but credentials no longer come from
`application.yml`.
```

- [ ] **Step 2: Run full verification**

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

Expected: only the planned persistent-auth files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe persistent auth identities"
```

## Spec Coverage Check

- Persistent auth users: covered by Tasks 1 and 2.
- Password hashing: covered by Task 2.
- Repository-backed login: covered by Task 3.
- Removal of config-backed users: covered by Task 4.
- JWT and merchant-scope regression safety: covered by Tasks 3 and 4.
- Documentation and end-to-end verification: covered by Task 5.

No spec sections are left without a matching implementation task.
