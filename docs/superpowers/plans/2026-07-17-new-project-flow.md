# New Project Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to create a real local project from valid text or already-confirmed file content, then enter that project's analysis entry route without leaving partial data on failure.

**Architecture:** `ProjectCreateForm.vue` owns deterministic validation and emits a normalized request. `NewProjectView.vue` coordinates `ProjectRepository.create()` and routing; the repository remains the only IndexedDB write boundary. Suggested model names are applied through a repository method that checks `userRenamed`, ready for the later analysis flow.

**Tech Stack:** Vue 3 Composition API, TypeScript strict mode, Vue Router, Dexie/IndexedDB, Vue Test Utils, Vitest.

## Global Constraints

- Text input must contain at least 5 Unicode characters after trimming unless valid confirmed file content is present.
- Temporary names use the first 20 Unicode characters of the normalized source text.
- Creating a project stores original text, confirmed file fields, and supplemental text in IndexedDB.
- A model-suggested name may replace the temporary name only while `userRenamed` is `false`.
- Creation errors keep the user on the form and must not leave a project behind.
- File parsing, 2 MB validation, preview, and privacy confirmation remain step 11 responsibilities.
- Do not add a server database, API keys, model calls, example projects, or sample prompts.

---

### Task 1: Creation Domain Rules

**Files:**
- Modify: `frontend/src/features/projects/types.ts`
- Modify: `frontend/src/features/projects/types.spec.ts`
- Modify: `frontend/src/db/repositories/projectRepository.ts`
- Modify: `frontend/src/db/repositories/projectRepository.spec.ts`

**Interfaces:**
- Produces: `createTemporaryProjectName(source: string): string`
- Produces: `ProjectCreateRepository.create(input: CreateProjectInput): Promise<Project>`
- Produces: `ProjectCreateRepository.applySuggestedName(projectId: string, suggestedName: string, now?: string): Promise<Project>`

- [ ] **Step 1: Write failing tests** for file-only project creation, 20-code-point temporary naming, suggested-name success, suggested-name failure preservation, and user-renamed preservation.
- [ ] **Step 2: Run targeted tests** with `npm --prefix frontend run test -- src/features/projects/types.spec.ts src/db/repositories/projectRepository.spec.ts`; expect failures for missing behavior.
- [ ] **Step 3: Implement domain and repository rules** so `createProject()` accepts nonblank text or nonblank file content, and `applySuggestedName()` updates only non-user-renamed projects in a project-table transaction.
- [ ] **Step 4: Re-run targeted tests** and expect all targeted tests to pass.

### Task 2: Deterministic Create Form

**Files:**
- Create: `frontend/src/features/projects/ProjectCreateForm.vue`
- Create: `frontend/src/features/projects/ProjectCreateForm.spec.ts`

**Interfaces:**
- Consumes: optional `fileInput: { name: string; content: string } | null`
- Produces event: `create(input: CreateProjectInput)`

- [ ] **Step 1: Write failing component tests** for fewer than 5 text characters, valid text, confirmed-file bypass, supplemental text, and disabled busy state.
- [ ] **Step 2: Run the component test** and expect failure because the component does not exist.
- [ ] **Step 3: Implement the form** with accessible labels, inline validation, character count, temporary-name preview, and a `开始分析` submit button.
- [ ] **Step 4: Re-run the component test** and expect it to pass.

### Task 3: Routes and Creation Coordinator

**Files:**
- Create: `frontend/src/views/NewProjectView.vue`
- Create: `frontend/src/views/NewProjectView.spec.ts`
- Create: `frontend/src/views/ProjectStartView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/ProjectHomeView.vue`
- Modify: `frontend/src/views/ProjectHomeView.spec.ts`

**Interfaces:**
- Route: `/projects/new`, name `new-project`
- Route: `/projects/:projectId`, name `project-start`, UUID route parameter
- Consumes: `ProjectCreateRepository`

- [ ] **Step 1: Write failing view/router tests** proving both home creation buttons navigate, successful creation routes with the new ID, and rejected creation stays on the form with an alert.
- [ ] **Step 2: Run targeted view tests** and expect missing route/component failures.
- [ ] **Step 3: Implement the views and routing**; the project-start route shows the saved project name and explains that AI analysis arrives in later steps without claiming it already runs.
- [ ] **Step 4: Re-run targeted tests** and expect them to pass.

### Task 4: Milestone Verification

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes all step 10 behavior.
- Produces verified milestone documentation.

- [ ] **Step 1: Run full frontend tests** with `npm --prefix frontend run test`; expect all tests to pass.
- [ ] **Step 2: Run strict type and production checks** with `npm --prefix frontend run typecheck` and `npm --prefix frontend run build`; expect success.
- [ ] **Step 3: Verify the browser flow** at 1280x800: both entry points reach the form, short input is rejected, valid input creates a persisted project and reaches `/projects/{id}`, with no console errors or horizontal overflow.
- [ ] **Step 4: Run unified production packaging** with `.\mvnw.cmd package`; expect both Maven modules to report `SUCCESS` and the current frontend assets to be present in the executable JAR.
- [ ] **Step 5: Update architecture and progress** using only behavior confirmed by the preceding checks.

## Self-Review

- Spec coverage: FR-001 text/file creation, FR-002 temporary/model naming boundaries, IndexedDB persistence, routing, and failure preservation each map to a task.
- Scope boundary: file decoding, file limits, preview, privacy confirmation, and actual model analysis are explicitly deferred to their scheduled steps.
- Type consistency: the form emits `CreateProjectInput`; the view consumes `ProjectCreateRepository`; both repository methods return `Project`.
