# Maven Aggregate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the step-2 Maven aggregate and a minimal Spring Boot backend whose application context is verified by tests.

**Architecture:** The repository root is both the Maven aggregator and shared parent. The `backend` module contains the only runnable application; it uses reactive WebFlux and deliberately has no persistence dependency or database configuration.

**Tech Stack:** Java 21, Maven Wrapper, Spring Boot 4.1.0, Spring AI BOM 2.0.0, WebFlux, Jakarta Validation, Actuator, Caffeine, JUnit 5, Spring Boot Test.

## Global Constraints

- Implement only `memory-bank/implementation-plan.md` step 2.
- Do not add database, MVC, frontend, AI provider, Redis, messaging, or security dependencies.
- Keep Java at 21, Spring Boot on 4.1.x, and Spring AI on 2.0.x.
- Update `memory-bank/architecture.md` and `memory-bank/progress.md` only with verified outcomes.

---

### Task 1: Create the Maven aggregate and backend module

**Files:**
- Create: `pom.xml`
- Create: `backend/pom.xml`

**Interfaces:**
- Consumes: Java 21 and Maven Central artifacts.
- Produces: root reactor module `backend` and dependency management for Spring AI 2.0.0.

- [ ] **Step 1: Create the root parent/aggregator POM**

Declare `packaging=pom`, module `backend`, Java 21, Spring Boot 4.1.0 as parent, and import `org.springframework.ai:spring-ai-bom:2.0.0` under dependency management.

- [ ] **Step 2: Create the backend module POM**

Add only `spring-boot-starter-webflux`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `caffeine`, and `spring-boot-starter-test`; configure the Spring Boot Maven plugin.

- [ ] **Step 3: Inspect the effective dependency tree**

Run: `mvn -pl backend dependency:tree`

Expected: WebFlux uses Reactor Netty; no JDBC, JPA, R2DBC, or database driver appears.

### Task 2: Add the application and context smoke test

**Files:**
- Create: `backend/src/main/java/com/prompt2prd/Prompt2PrdApplication.java`
- Create: `backend/src/test/java/com/prompt2prd/Prompt2PrdApplicationTests.java`

**Interfaces:**
- Consumes: Spring Boot auto-configuration from the backend module.
- Produces: `Prompt2PrdApplication.main(String[])` and a JUnit context smoke test.

- [ ] **Step 1: Add the application entry point**

Use `@SpringBootApplication` and `SpringApplication.run(Prompt2PrdApplication.class, args)`.

- [ ] **Step 2: Add the context smoke test**

Use `@SpringBootTest` with an empty `contextLoads()` test. The test intentionally supplies no database properties so an accidental database dependency would be visible during context startup.

- [ ] **Step 3: Run the module test**

Run: `mvn -pl backend test`

Expected: one test passes and the reactive application context starts.

### Task 3: Add the Maven Wrapper and verify the reactor

**Files:**
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `.mvn/wrapper/maven-wrapper.properties`

**Interfaces:**
- Consumes: Apache Maven Wrapper plugin.
- Produces: repository-local Maven 3.9.11 launcher for Windows and Unix-like environments using the official `only-script` distribution type.

- [ ] **Step 1: Generate the wrapper**

Run in PowerShell: `mvn wrapper:wrapper "-Dmaven=3.9.11"`

Expected: wrapper scripts and `.mvn/wrapper` files are created without changing module sources.

- [ ] **Step 2: Run the required repository command**

Run: `.\mvnw.cmd test`

Expected: the root reactor and backend module succeed with one passing test.

### Task 4: Record verified milestone state

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: successful wrapper test and dependency inspection.
- Produces: current-state documentation for the next implementation window.

- [ ] **Step 1: Update architecture**

Record the root-parent/backend-module build boundary, exact locked versions, reactive runtime, and absence of persistence dependencies.

- [ ] **Step 2: Update progress**

Mark step 2 complete only after `.\mvnw.cmd test` passes; record the test count and make step 3 the next action.

- [ ] **Step 3: Review the final diff**

Run: `git status --short` and `git diff --check --no-index NUL <each new text file>` as applicable.

Expected: only step-2 implementation, its plan, and milestone documentation are present; no secrets or generated project data are added.
