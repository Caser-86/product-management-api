# Pricing Scheduler Locking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect scheduled price execution with a database-backed distributed lock so only one application node scans and applies due price schedules at a time.

**Architecture:** Keep the current `@Scheduled` runner and `PricingService.applyDueSchedules(limit)` flow, but add ShedLock backed by the shared datasource and a Flyway-managed lock table. Externalize lock timing through configuration so local development stays simple while multi-instance deployments gain safe scheduler coordination.

**Tech Stack:** Spring Boot 3, Spring Scheduling, ShedLock JDBC provider, Flyway, MySQL/H2, JUnit 5, Spring Boot Test, Mockito

---

## File Map

### Existing files to modify

- `backend/build.gradle.kts`
  Add ShedLock dependencies.
- `backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java`
  Protect scheduled execution with a distributed lock and keep the delegation boundary clear.
- `backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java`
  Enable scheduler locking support.
- `backend/src/main/resources/application.yml`
  Add lock-related scheduler properties with safe defaults.
- `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`
  Extend regression coverage so due schedules still apply after locking changes.
- `backend/README.md`
  Document the new lock table and scheduler behavior.

### New production files to create

- `backend/src/main/java/com/example/ecommerce/shared/config/ShedLockConfiguration.java`
  Provide the JDBC lock provider bean backed by the app datasource.
- `backend/src/main/java/com/example/ecommerce/pricing/application/PricingScheduleProperties.java`
  Bind scheduler delay and lock properties in one focused configuration object.
- `backend/src/main/resources/db/migration/V10__create_shedlock_table.sql`
  Create the shared scheduler lock table.

### New test files to create

- `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java`
  Verify the runner executes through a lock-backed proxy and still delegates to `PricingService`.

## Task 1: Add lock table and ShedLock dependencies

**Files:**
- Modify: `backend/build.gradle.kts`
- Create: `backend/src/main/resources/db/migration/V10__create_shedlock_table.sql`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java`

- [ ] **Step 1: Write a failing context test for the missing lock infrastructure**

Create `PriceScheduleRunnerLockTest.java` with a minimal context assertion:

```java
@SpringBootTest
class PriceScheduleRunnerLockTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void creates_lock_provider_bean() {
        assertThat(applicationContext.containsBean("lockProvider")).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails before ShedLock is added**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest.creates_lock_provider_bean' --no-daemon
```

Expected: FAIL because no `lockProvider` bean exists yet.

- [ ] **Step 3: Add ShedLock dependencies and the Flyway migration**

Update `build.gradle.kts` with:

```kotlin
implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")
```

Create `V10__create_shedlock_table.sql`:

```sql
create table shedlock (
    name varchar(64) not null primary key,
    lock_until timestamp(3) not null,
    locked_at timestamp(3) not null,
    locked_by varchar(255) not null
);
```

- [ ] **Step 4: Re-run the test**

Run the same command from Step 2.

Expected: still FAIL, but now for missing configuration rather than missing classes or migration support.

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/resources/db/migration/V10__create_shedlock_table.sql backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java
git commit -m "feat: add scheduler lock dependencies"
```

## Task 2: Configure ShedLock and scheduler properties

**Files:**
- Create: `backend/src/main/java/com/example/ecommerce/shared/config/ShedLockConfiguration.java`
- Create: `backend/src/main/java/com/example/ecommerce/pricing/application/PricingScheduleProperties.java`
- Modify: `backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java`

- [ ] **Step 1: Extend the failing test to assert the lock provider type**

Add to `PriceScheduleRunnerLockTest.java`:

```java
@Autowired
private LockProvider lockProvider;

@Test
void creates_jdbc_lock_provider() {
    assertThat(lockProvider).isNotNull();
}
```

- [ ] **Step 2: Run the test to confirm configuration is still missing**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest.creates_jdbc_lock_provider' --no-daemon
```

Expected: FAIL because the lock provider bean is not configured yet.

- [ ] **Step 3: Add configuration and properties binding**

Create `PricingScheduleProperties.java`:

```java
@ConfigurationProperties(prefix = "pricing.schedule")
public record PricingScheduleProperties(
    long fixedDelayMs,
    String lockName,
    String lockAtMostFor,
    String lockAtLeastFor
) {
}
```

Create `ShedLockConfiguration.java`:

```java
@Configuration
public class ShedLockConfiguration {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
```

Update `SchedulingConfiguration.java`:

```java
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PricingScheduleProperties.class)
@EnableSchedulerLock(defaultLockAtMostFor = "${pricing.schedule.lock-at-most-for:PT1M}")
public class SchedulingConfiguration {
}
```

Update `application.yml`:

```yaml
pricing:
  schedule:
    fixed-delay-ms: ${PRICING_SCHEDULE_FIXED_DELAY_MS:30000}
    lock-name: ${PRICING_SCHEDULE_LOCK_NAME:pricing.due-schedule-runner}
    lock-at-most-for: ${PRICING_SCHEDULE_LOCK_AT_MOST_FOR:PT1M}
    lock-at-least-for: ${PRICING_SCHEDULE_LOCK_AT_LEAST_FOR:PT5S}
```

- [ ] **Step 4: Run the lock configuration tests**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest.creates_lock_provider_bean' --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest.creates_jdbc_lock_provider' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/shared/config/ShedLockConfiguration.java backend/src/main/java/com/example/ecommerce/pricing/application/PricingScheduleProperties.java backend/src/main/java/com/example/ecommerce/shared/config/SchedulingConfiguration.java backend/src/main/resources/application.yml backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java
git commit -m "feat: configure scheduler locking"
```

## Task 3: Protect the scheduled runner with a distributed lock

**Files:**
- Modify: `backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java`
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java`

- [ ] **Step 1: Add a failing delegation test for the runner**

Extend `PriceScheduleRunnerLockTest.java` with:

```java
@MockitoBean
private PricingService pricingService;

@Autowired
private PriceScheduleRunner priceScheduleRunner;

@Test
void runner_executes_with_lock_and_delegates_to_pricing_service() {
    given(pricingService.applyDueSchedules(20)).willReturn(3);

    int applied = priceScheduleRunner.runDueSchedules();

    assertThat(applied).isEqualTo(3);
    then(pricingService).should().applyDueSchedules(20);
}
```

- [ ] **Step 2: Run the new test**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest.runner_executes_with_lock_and_delegates_to_pricing_service' --no-daemon
```

Expected: PASS or FAIL depending on proxy shape, but it does not yet prove the method is lock-backed.

- [ ] **Step 3: Tighten the test to assert lock context and annotate the runner**

Update the test method:

```java
@Test
void runner_executes_with_lock_and_delegates_to_pricing_service() {
    given(pricingService.applyDueSchedules(20)).willAnswer(invocation -> {
        LockAssert.assertLocked();
        return 3;
    });

    int applied = priceScheduleRunner.runDueSchedules();

    assertThat(applied).isEqualTo(3);
    then(pricingService).should().applyDueSchedules(20);
}
```

Update `PriceScheduleRunner.java`:

```java
@Component
public class PriceScheduleRunner {

    private static final int DUE_SCHEDULE_BATCH_SIZE = 20;

    private final PricingService pricingService;
    private final PricingScheduleProperties pricingScheduleProperties;

    public PriceScheduleRunner(
        PricingService pricingService,
        PricingScheduleProperties pricingScheduleProperties
    ) {
        this.pricingService = pricingService;
        this.pricingScheduleProperties = pricingScheduleProperties;
    }

    @Scheduled(fixedDelayString = "${pricing.schedule.fixed-delay-ms:30000}")
    @SchedulerLock(
        name = "${pricing.schedule.lock-name:pricing.due-schedule-runner}",
        lockAtMostFor = "${pricing.schedule.lock-at-most-for:PT1M}",
        lockAtLeastFor = "${pricing.schedule.lock-at-least-for:PT5S}"
    )
    public int runDueSchedules() {
        return pricingService.applyDueSchedules(DUE_SCHEDULE_BATCH_SIZE);
    }
}
```

- [ ] **Step 4: Run the runner lock test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.application.PriceScheduleRunnerLockTest' --no-daemon
```

Expected: PASS, proving the runner method executes inside ShedLock's lock context and still delegates to `PricingService`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/ecommerce/pricing/application/PriceScheduleRunner.java backend/src/test/java/com/example/ecommerce/pricing/application/PriceScheduleRunnerLockTest.java
git commit -m "feat: lock scheduled price execution"
```

## Task 4: Regress due schedule behavior after locking changes

**Files:**
- Modify: `backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java`

- [ ] **Step 1: Add a failing regression test that exercises due-schedule application through the service path**

Add:

```java
@Test
void due_schedules_are_applied_once() throws Exception {
    MvcResult createScheduleResult = mockMvc.perform(withBearer(post("/admin/skus/{skuId}/price-schedules", skuId), platformAdminToken(9001L, 2001L))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "listPrice": 399.00,
                  "salePrice": 329.00,
                  "effectiveAt": "%s",
                  "reason": "flash drop",
                  "operatorId": 7002
                }
                """.formatted(LocalDateTime.now().minusMinutes(1))))
        .andExpect(status().isOk())
        .andReturn();

    long scheduleId = objectMapper.readTree(createScheduleResult.getResponse().getContentAsString())
        .path("data")
        .path("scheduleId")
        .asLong();

    mockMvc.perform(withBearer(post("/admin/price-schedules/{scheduleId}/apply", scheduleId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isOk());

    mockMvc.perform(withBearer(post("/admin/price-schedules/{scheduleId}/apply", scheduleId), platformAdminToken(9001L, 2001L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRICE_SCHEDULE_CONFLICT"));
}
```

- [ ] **Step 2: Run the regression test**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.api.PricingControllerTest.due_schedules_are_applied_once' --no-daemon
```

Expected: PASS if existing pending/applied behavior still holds. If it fails, fix the scheduling path before moving on.

- [ ] **Step 3: Keep the controller regression focused**

Do not add scheduler-timing tests here. This class should only verify business behavior still matches API expectations after locking changes.

- [ ] **Step 4: Re-run the pricing test class**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests 'com.example.ecommerce.pricing.api.PricingControllerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/example/ecommerce/pricing/api/PricingControllerTest.java
git commit -m "test: cover scheduled pricing after locking"
```

## Task 5: Document and verify the multi-instance scheduler model

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Update README with lock-backed scheduling details**

Add a note under scheduling that covers:

- scheduled pricing is protected by a shared database lock
- the lock table name is `shedlock`
- key properties:
  - `pricing.schedule.fixed-delay-ms`
  - `pricing.schedule.lock-name`
  - `pricing.schedule.lock-at-most-for`
  - `pricing.schedule.lock-at-least-for`

- [ ] **Step 2: Run full backend verification**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect the final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only pricing-scheduler-locking related files are modified.

- [ ] **Step 4: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe scheduler locking"
```

## Spec Coverage Check

- Shared lock table and dependency choice: covered by Task 1.
- Scheduler lock configuration and externalized timing: covered by Task 2.
- Locking the existing runner instead of changing APIs: covered by Task 3.
- Preserving pricing behavior and duplicate safety: covered by Task 4.
- Operator documentation and verification: covered by Task 5.

No spec sections are left without an implementation task.
