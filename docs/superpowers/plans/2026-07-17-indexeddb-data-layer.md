# IndexedDB Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the browser-persisted domain contracts and create the version-1 Dexie database required by Prompt2PRD.

**Architecture:** Runtime-validated factories create valid project and requirement records before persistence. A focused `AppDatabase` class owns only schema/version declarations; later repositories will own operations and transactions.

**Tech Stack:** TypeScript 6, Dexie 4, IndexedDB, Vitest, fake-indexeddb.

## Global Constraints

- Combine authoritative implementation-plan steps 6 and 7 because the requested data layer requires both types and schema.
- Use UUID identifiers and UTC ISO-8601 persisted timestamps.
- Requirement status is exactly `INFERRED`, `PENDING`, `CONFIRMED`, or `CONFLICTED`.
- Only `CONFIRMED` requirements may have `locked = true`.
- Database version is exactly 1 and contains only the nine documented object stores.
- Never persist API keys, Authorization headers, or model credentials.
- Do not implement project repositories or UI integration in this milestone.

---

### Task 1: Define validated domain contracts

**Files:**
- Create: `frontend/src/features/projects/types.ts`
- Create: `frontend/src/features/projects/types.spec.ts`
- Create: `frontend/src/features/requirements/types.ts`
- Create: `frontend/src/features/requirements/types.spec.ts`
- Create: `frontend/src/features/prd/types.ts`

**Interfaces:**
- Produces: `Project`, `RequirementItem`, question/answer/conflict/version/change, completeness, architecture-candidate and PRD-section types.
- Produces: `createProject()` and `createRequirementItem()` runtime validation boundaries.

- [ ] **Step 1: Write factory tests**

Cover valid construction, invalid UUID, non-UTC timestamp, completeness outside 0–100, invalid status, locking a non-confirmed item, and preservation of source metadata.

- [ ] **Step 2: Run focused tests and observe failure**

Run `npm --prefix frontend run test -- src/features/projects/types.spec.ts src/features/requirements/types.spec.ts`; expected result before implementation is missing-module failure.

- [ ] **Step 3: Implement focused types and factories**

Use string-literal unions for persisted enums and validation functions that throw `TypeError` for malformed identifiers, timestamps, enums, or illegal lock state.

- [ ] **Step 4: Run focused tests**

The two factory test files must pass without touching IndexedDB.

### Task 2: Declare and verify Dexie schema version 1

**Files:**
- Create: `frontend/src/db/schema.ts`
- Create: `frontend/src/db/appDatabase.ts`
- Create: `frontend/src/db/appDatabase.spec.ts`
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`

**Interfaces:**
- Consumes: all persisted domain record types from Task 1.
- Produces: `DATABASE_VERSION`, `DATABASE_STORES`, `AppDatabase`, and `createAppDatabase(name)`.

- [ ] **Step 1: Install isolated IndexedDB test support**

Run `npm --prefix frontend install --save-dev fake-indexeddb`; lock the resolved version.

- [ ] **Step 2: Write schema tests**

Test first open, all nine store names, primary/secondary indexes, a write followed by close/reopen, version number 1, and absence of API-key or credential stores/fields.

- [ ] **Step 3: Implement the schema**

Declare `project`, `requirement_item`, `clarification_question`, `clarification_answer`, `requirement_conflict`, `requirement_version`, `requirement_change`, `prd_section`, and `app_setting`. Add project-scoped compound indexes needed by later repositories.

- [ ] **Step 4: Run all frontend checks**

Run `npm --prefix frontend run test`, `npm --prefix frontend run typecheck`, and `npm --prefix frontend run build`; all must pass.

### Task 3: Record the verified milestone

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: passing tests and build evidence.
- Produces: current-state documentation that names the schema version, store boundary, validation rules, and the next repository milestone.

- [ ] **Step 1: Update architecture**

Document the domain factory boundary, Dexie v1 store list, project-scoped indexes, test isolation, and explicit credential exclusion.

- [ ] **Step 2: Update progress**

Record commands and counts, note that repositories/UI are not yet connected, and identify project Repository implementation as next.
