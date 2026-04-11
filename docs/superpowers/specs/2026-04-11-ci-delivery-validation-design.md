# CI Delivery Validation Design

- Date: 2026-04-11
- Status: Draft
- Scope: Expand GitHub Actions validation for backend packaging and container delivery

## 1. Goal

This design hardens the repository's delivery pipeline by validating more than
just unit and integration tests.

The project already has a working GitHub Actions workflow that runs:

- `./gradlew clean test --no-daemon`

That is a solid baseline, but it still leaves two delivery gaps:

- the executable jar can drift without CI noticing
- the Docker packaging path can break even when tests remain green

The goal of this phase is to add CI checks that prove:

- backend tests still pass
- the backend can be packaged into a runnable jar
- the backend Docker image can be built from the checked-in `Dockerfile`

This phase is intentionally about delivery confidence, not new product
features.

## 2. Recommended Approach

Extend the existing GitHub Actions workflow with separate jobs for tests,
bootJar packaging, and Docker image build validation.

Why this approach:

- it improves signal without introducing a second workflow
- it keeps delivery checks readable and easy to maintain
- it matches the current project shape, which has one backend service and one
  Dockerfile

## 3. Alternatives Considered

### 3.1 Recommended: single workflow with three validation jobs

- keep one workflow file
- add dedicated jobs for `clean test`, `bootJar`, and Docker build
- upload the built jar as a workflow artifact

Pros:

- simple mental model
- clear failure isolation by job
- enough coverage for current delivery needs

Cons:

- slightly longer CI runtime than test-only validation

### 3.2 Lighter: tests plus bootJar only

Pros:

- faster pipeline
- no Docker dependency in CI

Cons:

- Dockerfile drift still goes undetected

### 3.3 Heavier: full container smoke test with MySQL

Pros:

- strongest delivery validation

Cons:

- adds startup timing complexity
- slower and noisier than the project currently needs

## 4. Scope Boundaries

Included:

- GitHub Actions validation for tests
- GitHub Actions validation for `bootJar`
- GitHub Actions validation for Docker image build
- artifact upload for the built jar
- documentation updates for the stronger CI contract

Excluded:

- deployment to any real environment
- registry push or release publication
- Docker Compose integration tests
- release tagging automation
- semantic version automation

## 5. Workflow Design

Keep the existing workflow file:

- `.github/workflows/backend-ci.yml`

Recommended job structure:

- `test`
- `package`
- `docker`

### 5.1 Test job

Responsibilities:

- check out the repository
- set up JDK 21 with Gradle cache
- run `./gradlew clean test --no-daemon`

This remains the correctness gate.

### 5.2 Package job

Responsibilities:

- build the backend jar with `./gradlew bootJar --no-daemon`
- upload the generated jar as a workflow artifact

This proves packaging remains healthy and gives a downloadable artifact for
inspection if needed.

### 5.3 Docker job

Responsibilities:

- build the backend Docker image from `backend/Dockerfile`

Suggested command shape:

- `docker build -t product-management-api-ci ./backend`

This validates the checked-in Dockerfile and the jar build inside the container
path.

## 6. Dependency and Execution Order

Recommended dependency graph:

- `package` depends on `test`
- `docker` depends on `test`

Reasoning:

- test failures should stop delivery validation early
- package and Docker validation can still run independently after test success

This keeps the workflow fast enough while avoiding duplicate failure noise when
the code itself is already red.

## 7. Artifact Policy

Upload the jar from the package job.

Recommended artifact:

- name: `backend-bootjar`
- path: `backend/build/libs/*.jar`

This phase does not require long artifact retention tuning or release uploads.

## 8. Failure Semantics

The workflow should fail when:

- tests fail
- jar packaging fails
- Docker image build fails

This makes CI a true delivery gate instead of just a correctness smoke check.

## 9. Documentation Updates

Update backend documentation to note that CI now validates:

- test suite
- bootJar packaging
- Docker image build

This helps future contributors understand why workflow runtime increased and
what failures mean.

## 10. Testing Strategy

Because GitHub Actions logic is configuration rather than runtime business code,
this phase will rely on:

- local backend verification with Gradle commands
- careful workflow diff review
- Docker validation remaining declarative in CI even if local Docker is not
  available in the current environment

Fresh local verification for this phase should include:

- `.\gradlew.bat clean test --no-daemon`
- `.\gradlew.bat bootJar --no-daemon`

If Docker is unavailable locally, that limitation should be documented in the
delivery note rather than guessed away.

## 11. Rollout Plan

Suggested implementation order:

1. update the GitHub Actions workflow structure
2. add artifact upload for the packaged jar
3. update README with CI coverage
4. run local Gradle verification

## 12. Success Criteria

This phase is complete when:

- GitHub Actions validates tests, jar packaging, and Docker image build
- the workflow remains readable and maintainable in one file
- the backend README reflects the stronger CI contract
- fresh local verification confirms tests and `bootJar` both succeed
