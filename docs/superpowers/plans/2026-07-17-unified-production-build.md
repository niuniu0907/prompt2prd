# Unified Production Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the root Maven package command build the locked Vue frontend, embed it in the Spring Boot executable JAR, and serve frontend plus API infrastructure from one origin.

**Architecture:** The root aggregator owns the production-only frontend toolchain and runs it during `prepare-package`, so ordinary Maven tests do not rebuild Vue. The backend module copies the completed `frontend/dist` tree into its Maven output directory before Spring Boot repackages the JAR.

**Tech Stack:** Maven Wrapper 3.9.11, frontend-maven-plugin 1.15.4, Node.js 24.14.0 LTS, npm 11.9.0, Maven Resources Plugin 3.5.0, Vite 8.1.5, Spring Boot 4.1.0.

## Global Constraints

- Implement only `memory-bank/implementation-plan.md` step 4.
- `npm ci` must consume the committed `frontend/package-lock.json`; Maven must not update dependency versions.
- The frontend build runs only for `package` or later lifecycle phases; frontend and backend remain independently runnable during development.
- Copy static files without Maven filtering and do not add CORS configuration.
- Do not implement SPA history fallback, workspace UI, routes, or design tokens in this step.

---

### Task 1: Add the production frontend lifecycle to the root aggregate

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: `frontend/package.json`, `frontend/package-lock.json`, and npm `build` script.
- Produces: a freshly built `frontend/dist` before backend packaging begins.

- [ ] **Step 1: Lock frontend build tool versions**

Add Maven properties for `frontend-maven-plugin` 1.15.4, Node.js v24.14.0, and npm 11.9.0.

- [ ] **Step 2: Install the local frontend toolchain during production packaging**

Configure `frontend-maven-plugin` with `workingDirectory=frontend`, a root `target` install directory, `inherited=false`, and an `install-node-and-npm` execution in `prepare-package`.

- [ ] **Step 3: Install and build from the lockfile**

In the same phase and declaration order, execute `npm ci` and `npm run build`. The build already runs strict Vue and Node configuration type checks before Vite.

### Task 2: Embed frontend output in the backend JAR

**Files:**
- Modify: `backend/pom.xml`

**Interfaces:**
- Consumes: `${project.parent.basedir}/frontend/dist`.
- Produces: `${project.build.outputDirectory}/static/index.html` and hashed assets included by the Spring Boot repackage goal.

- [ ] **Step 1: Configure resource copying**

Bind Maven Resources Plugin 3.5.0 `copy-resources` to `process-resources`, copy the complete Vite output to `${project.build.outputDirectory}/static`, and set `filtering=false`.

- [ ] **Step 2: Run the aggregate package**

Run: `.\mvnw.cmd package`

Expected: Node/npm installation, `npm ci`, strict frontend build, backend test, static resource copy, and Spring Boot repackaging all succeed.

- [ ] **Step 3: Inspect the JAR**

Run: `jar tf backend/target/backend-0.0.1-SNAPSHOT.jar`

Expected: `BOOT-INF/classes/static/index.html` and a hashed JavaScript asset are present.

### Task 3: Verify the single-origin runtime

**Files:**
- Verify: `backend/target/backend-0.0.1-SNAPSHOT.jar`

**Interfaces:**
- Consumes: Java 21 and the packaged JAR.
- Produces: one local HTTP origin serving both Vue and Spring Boot Actuator.

- [ ] **Step 1: Start the packaged application**

Run the JAR on an unused local port with a hidden process and wait until `/actuator/health` responds.

- [ ] **Step 2: Verify frontend and health responses**

Request `/` and assert HTTP 200 plus the Vite module script; request `/actuator/health` and assert HTTP 200 plus `UP`.

- [ ] **Step 3: Stop the verified process**

Stop only the exact Java process started by this verification.

### Task 4: Record verified milestone state

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: successful package, JAR inspection, and HTTP checks.
- Produces: current-state documentation pointing to step 5.

- [ ] **Step 1: Update architecture**

Record the root production lifecycle, static resource path, single-origin runtime, and independent development boundary.

- [ ] **Step 2: Update progress**

Record command outcomes and make step 5 the next action only after runtime verification passes.

- [ ] **Step 3: Review scope**

Inspect the POM changes and confirm no CORS configuration, SPA fallback, routes, or workspace UI were added.
