# Project Home List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static project-home empty state with a real IndexedDB-backed activity/archive/recycle-bin workspace and actionable project cards.

**Architecture:** `ProjectRepository.listSummaries()` supplies projects plus pending requirement/question counts. `ProjectHomeView` owns the selected lifecycle view, async loading/error state, and Repository commands; focused presentational components render cards, lists, and view-specific empty states.

**Tech Stack:** Vue 3 SFCs, TypeScript 6, Dexie, Vitest, Vue Test Utils, Playwright CLI.

## Global Constraints

- Implement `memory-bank/implementation-plan.md` step 9 only; do not build the step-10 creation form or project analysis route.
- Read real projects from IndexedDB through `ProjectRepository`; never add sample or pet-boarding data to source code.
- Show project name, current stage, completeness, pending count, and updated time.
- Keep actions inside an explicit card menu; do not permanently display a row of destructive buttons.
- Normal delete moves to the recycle bin; permanent delete requires explicit confirmation and remains limited to one project.
- Keep exact Prompt2PRD color tokens and light-only theme; use Airtable-inspired density, thin borders, and restrained shadows only.
- The desktop baseline remains 1280×800 with no horizontal overflow.

---

### Task 1: Add project summaries to the Repository

**Files:**
- Modify: `frontend/src/db/repositories/projectRepository.ts`
- Modify: `frontend/src/db/repositories/projectRepository.spec.ts`

**Interfaces:**
- Produces: `ProjectSummary { project, pendingCount }` and `listSummaries(filter?)`.
- Pending count: `PENDING` requirement items plus `PENDING` clarification questions for the same project.

- [ ] **Step 1: Write the failing summary test**

Create two projects and seed pending/confirmed requirements and pending/answered questions; assert lifecycle filtering, updated-time ordering, and exact per-project pending counts.

- [ ] **Step 2: Implement the read transaction**

Reuse `list(filter)`, query the existing compound `[projectId+status]` indexes, and return immutable summary objects without changing project state.

- [ ] **Step 3: Run Repository tests**

Run `npm --prefix frontend run test -- src/db/repositories/projectRepository.spec.ts`; all Repository tests must pass.

### Task 2: Build the project list components

**Files:**
- Create: `frontend/src/features/projects/ProjectEmptyState.vue`
- Create: `frontend/src/features/projects/ProjectCard.vue`
- Create: `frontend/src/features/projects/ProjectList.vue`
- Create: `frontend/src/features/projects/ProjectCard.spec.ts`

**Interfaces:**
- `ProjectEmptyState`: consumes lifecycle view, emits `create` only for the active view.
- `ProjectCard`: consumes `ProjectSummary`, lifecycle view, and busy state; emits rename/copy/archive/trash/restore/permanent-delete.
- `ProjectList`: forwards card events for a list of summaries.

- [ ] **Step 1: Write card display and action tests**

Assert name, translated stage, progress, pending count, updated label, menu-contained operations, inline rename, and confirmed permanent delete.

- [ ] **Step 2: Implement focused components**

Use semantic `article`, `progress`, `details`, `form`, and buttons. Disable operations while busy and keep destructive actions visually distinct but not dominant.

- [ ] **Step 3: Run component tests**

Run the focused ProjectCard test and confirm all presentation/emit contracts pass.

### Task 3: Connect lifecycle navigation and Repository commands

**Files:**
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `frontend/src/layouts/AppShell.spec.ts`
- Modify: `frontend/src/views/ProjectHomeView.vue`
- Create: `frontend/src/views/ProjectHomeView.spec.ts`
- Modify: `frontend/src/App.spec.ts`

**Interfaces:**
- `AppShell`: consumes active section and emits navigation for active/archive/recycle-bin; model settings stays disabled.
- `ProjectHomeView`: accepts an optional Repository prop for isolated tests and defaults to the production singleton.

- [ ] **Step 1: Write view tests**

Mock Repository summaries and operations; cover empty states, lifecycle filtering, list rendering, rename, archive, trash, restore, permanent delete, loading, and failure feedback. Confirm no sample content exists.

- [ ] **Step 2: Implement navigation and async state**

Load on mount and after every mutation, ignore stale filter responses, expose busy/error/success status, and keep the new-project controls present but unwired until step 10.

- [ ] **Step 3: Run all frontend checks**

Run `npm --prefix frontend run test`, `npm --prefix frontend run typecheck`, and `npm --prefix frontend run build`; all must pass.

### Task 4: Verify in a real browser and record the milestone

**Files:**
- Create: `output/playwright/step-9-project-list-1280.png`
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: Vite development server and a temporary browser-local project created only for validation.
- Produces: semantic snapshot, console/layout evidence, screenshot, and verified current-state documentation.

- [ ] **Step 1: Run Playwright verification**

Open the page, seed one temporary project through the real Repository, reload, verify its metadata card, switch archive/recycle-bin views, check 1280×800 overflow and console errors, capture the screenshot, then remove only the temporary project.

- [ ] **Step 2: Update architecture and progress**

Record Repository-summary/UI boundaries, menu actions, tested browser evidence, known step-10 limitation, and step 10 as next.

- [ ] **Step 3: Verify unified packaging**

Run `.\mvnw.cmd package`; expect frontend build, backend test, static-resource copy, and executable JAR packaging to succeed.
