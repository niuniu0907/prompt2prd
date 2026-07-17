# Project Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the transactional local-project Repository for creation, lookup, filtered listing, rename, full copy, archive, recycle-bin moves, restore, and one-project permanent deletion.

**Architecture:** `ProjectRepository` receives an `AppDatabase` instance so production uses the shared Dexie database while tests use isolated databases. Single-project mutations use Dexie transactions; full copy remaps all project-owned identifiers and internal references, and permanent deletion scopes every related-table delete to one `projectId`.

**Tech Stack:** TypeScript 6, Dexie 4, IndexedDB, Vitest, fake-indexeddb.

## Global Constraints

- Implement only `memory-bank/implementation-plan.md` step 8; do not connect the project-home UI.
- Normal deletion sets `status = DELETED` and `deletedAt`; it does not remove records.
- Copy creates a full independent graph, appends `副本` to the name, generates new UUIDs, and resets archive/trash state.
- Archive preserves all related data; restore returns a deleted or archived project to `ACTIVE`.
- Permanent deletion removes only one explicit project and its rows from the eight project-owned stores; `app_setting` is never touched.
- All persisted mutation timestamps are UTC ISO-8601 and all multi-store writes are transactional.
- No batch empty-trash operation is introduced.

---

### Task 1: Define Repository behavior with isolated transaction tests

**Files:**
- Create: `frontend/src/db/repositories/projectRepository.spec.ts`

**Interfaces:**
- Consumes: `AppDatabase`, existing project/requirement/question/answer/conflict/version/change/PRD records.
- Verifies: `create`, `getById`, `list`, `rename`, `copy`, `archive`, `moveToTrash`, `restore`, and `permanentlyDelete`.

- [ ] **Step 1: Write creation, read, list, and rename tests**

Create multiple projects with deterministic UUIDs/times, verify active-list updated-time ordering, verify read after close/reopen, and assert rename changes only name/user-renamed/update metadata.

- [ ] **Step 2: Write archive, trash, and restore tests**

Assert list separation across `ACTIVE`, `ARCHIVED`, and `DELETED`; normal delete must retain all project-owned records and restoration must clear lifecycle timestamps.

- [ ] **Step 3: Write full-copy and permanent-delete tests**

Seed all eight project-owned stores, verify copied rows have a new project ID and remapped internal IDs/references, then mutate the copy and prove the original is unchanged. Permanently delete one project and verify every related row for that project is gone while a second project and `app_setting` survive.

- [ ] **Step 4: Run the focused test and observe failure**

Run `npm --prefix frontend run test -- src/db/repositories/projectRepository.spec.ts`; expected result is a missing Repository module.

### Task 2: Implement the transactional Repository

**Files:**
- Create: `frontend/src/db/repositories/projectRepository.ts`

**Interfaces:**
- Produces: `ProjectRepository`, `ProjectNotFoundError`, `ProjectListFilter`, and production `projectRepository`.
- Method signatures: `create(input)`, `getById(id)`, `list(filter?)`, `rename(id, name, now?)`, `copy(id, options?)`, `archive(id, now?)`, `moveToTrash(id, now?)`, `restore(id, now?)`, and `permanentlyDelete(id)`.

- [ ] **Step 1: Implement create/read/list and lifecycle updates**

Use the existing `createProject` factory, validate UUID/time input, reject missing projects, trim rename input, sort lists by `updatedAt` descending, and apply status/timestamp changes in read-write transactions.

- [ ] **Step 2: Implement a full graph copy**

Load all rows by source `projectId`; allocate maps for requirement, question, option, answer, conflict, version, change, PRD-section and question-batch UUIDs; write cloned rows with the new project ID and remap source, selection, conflict, version/change, and snapshot references.

- [ ] **Step 3: Implement scoped permanent deletion**

Require the project to exist, then delete rows selected by that exact `projectId` from all eight project-owned stores and finally delete the project in one transaction. Do not touch settings or other projects.

- [ ] **Step 4: Run focused and complete frontend checks**

Run the focused test, `npm --prefix frontend run test`, `npm --prefix frontend run typecheck`, and `npm --prefix frontend run build`; all must pass.

### Task 3: Record verified Repository architecture

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: passing transaction tests and build evidence.
- Produces: current-state documentation naming Repository transaction boundaries, copy semantics, deletion scope, limitations, and step 9 as next.

- [ ] **Step 1: Update architecture and progress**

Record only verified behavior, test counts, and that the UI is still not connected.

- [ ] **Step 2: Run the unified production package**

Run `.\mvnw.cmd package`; expect frontend lock installation/typecheck/build, backend test, resource copy, and Spring Boot JAR packaging to succeed.
