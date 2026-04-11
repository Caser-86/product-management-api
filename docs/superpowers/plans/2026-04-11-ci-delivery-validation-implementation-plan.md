# CI Delivery Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand GitHub Actions so CI validates backend tests, executable jar packaging, and Docker image build viability.

**Architecture:** Keep the existing single workflow file, but split validation into focused jobs for tests, packaging, and Docker build. Preserve the current backend-only workflow scope while adding artifact upload and stronger delivery guarantees.

**Tech Stack:** GitHub Actions, Gradle, Spring Boot bootJar packaging, Docker build, Java 21

---

## File Map

### Existing files to modify

- `.github/workflows/backend-ci.yml`
  Expand the workflow into test, package, and Docker validation jobs.
- `backend/README.md`
  Document that CI now checks tests, bootJar packaging, and Docker build.

### New files to create

None.

## Task 1: Expand the GitHub Actions workflow structure

**Files:**
- Modify: `.github/workflows/backend-ci.yml`

- [ ] **Step 1: Review the current workflow and identify the exact job split**

Current workflow has one `test` job only. The target shape is:

- `test`
- `package`
- `docker`

Keep the same triggers:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

- [ ] **Step 2: Update the workflow to keep `test` as the first gate**

The `test` job should continue to:

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew clean test --no-daemon
```

- [ ] **Step 3: Add a `package` job that depends on test**

Add a second job:

```yaml
  package:
    runs-on: ubuntu-latest
    needs: test
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew bootJar --no-daemon
```

- [ ] **Step 4: Add a `docker` job that depends on test**

Add:

```yaml
  docker:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - name: Build backend Docker image
        run: docker build -t product-management-api-ci ./backend
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/backend-ci.yml
git commit -m "ci: validate packaging and docker build"
```

## Task 2: Upload the bootJar as a workflow artifact

**Files:**
- Modify: `.github/workflows/backend-ci.yml`

- [ ] **Step 1: Add artifact upload to the package job**

Add this step after `bootJar`:

```yaml
      - name: Upload backend bootJar
        uses: actions/upload-artifact@v4
        with:
          name: backend-bootjar
          path: backend/build/libs/*.jar
```

- [ ] **Step 2: Check workflow readability after the artifact step**

Keep the artifact upload inside the `package` job and do not duplicate build
steps in the Docker job.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/backend-ci.yml
git commit -m "ci: publish backend bootjar artifact"
```

## Task 3: Update documentation for stronger CI guarantees

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: Add a short CI section to the README**

Document that CI now validates:

- `clean test`
- `bootJar`
- backend Docker image build

Suggested wording:

```md
## Continuous Integration

GitHub Actions validates three delivery checks on pushes and pull requests to
`main`:

- backend tests with `./gradlew clean test --no-daemon`
- jar packaging with `./gradlew bootJar --no-daemon`
- Docker image build from `backend/Dockerfile`
```

- [ ] **Step 2: Keep the README focused on what CI proves**

Do not describe registry publishing or deployment, because this phase does not
implement either of those.

- [ ] **Step 3: Commit**

```bash
git add backend/README.md
git commit -m "docs: describe stronger ci delivery checks"
```

## Task 4: Run local verification for test and packaging paths

**Files:**
- No code changes expected unless verification reveals an issue

- [ ] **Step 1: Run the full backend test suite**

Run from `D:\Program Files\product-management-api\backend`:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean test --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run jar packaging locally**

Run from the same directory:

```powershell
$env:JAVA_HOME='D:\Program Files\product-management-api\.tools\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat bootJar --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect final diff**

Run:

```powershell
git status --short
git diff --stat
```

Expected: only workflow and README changes are present for this phase.

## Spec Coverage Check

- Test job retention: covered by Task 1.
- bootJar validation: covered by Tasks 1 and 4.
- Docker image build validation: covered by Task 1.
- artifact upload: covered by Task 2.
- README update: covered by Task 3.
- local verification expectations: covered by Task 4.

No spec sections are left without an implementation task.
