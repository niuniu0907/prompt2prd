# Repository Guidelines

## Project Structure & Module Organization

This repository is in the design phase. Treat `memory-bank/design-doc.md` as the authoritative specification, `memory-bank/tech-stack.md` as the approved stack, `memory-bank/implementation-plan.md` as the execution sequence, and `HANDOFF.md` as the current-state summary. No application source or runnable tests exist yet.

The planned structure is `frontend/` for Vue 3 and TypeScript, `backend/` for Spring Boot, and a root aggregate `pom.xml`. Group frontend code under `src/features/`; keep persistence in `src/db/`, API/SSE code in `src/api/`, and pages in `src/views/`. Use backend capability packages such as `analysis`, `generation`, `model`, `stream`, and `quota`.

## Build, Test, and Development Commands

There are no runnable commands until the project skeleton is created. After scaffolding, standardize on:

- `npm --prefix frontend install` — install locked frontend dependencies.
- `npm --prefix frontend run dev` — start the Vite development server.
- `npm --prefix frontend run test` — run Vitest tests.
- `.\mvnw.cmd test` — run backend unit and integration tests.
- `.\mvnw.cmd package` — build the frontend, package it into Spring Boot, and produce the runnable JAR.

Do not document a command as supported until it works in this repository.

## Coding Style & Naming Conventions

Use two-space indentation for Vue, TypeScript, CSS, JSON, and YAML; use four spaces for Java. Components use `PascalCase.vue`, composables use `useFeature.ts`, and tests use `*.spec.ts`. Java types use `PascalCase`; methods and fields use `camelCase`. Prefer records for immutable DTOs. Use `snake_case` only where the documented SSE protocol requires it.

## Testing Guidelines

Use Vitest and Vue Test Utils for frontend tests, JUnit 5 and `WebTestClient` for backend APIs and SSE, and Playwright for critical flows. Test malformed AI output, duplicate events, cancellation, IndexedDB failures, and preservation of confirmed state. Place tests beside frontend features or under `backend/src/test/java`.

## Commit & Pull Request Guidelines

No usable Git history exists, so use Conventional Commits such as `feat(analysis): validate model output`. Keep commits scoped. Pull requests should explain changes, list verification commands, link requirement IDs from `memory-bank/design-doc.md`, and include screenshots for UI changes. Never commit API keys, local project data, generated PRDs, or unredacted model payloads.

## Agent-Specific Instructions

Do not bulk-delete files or directories. Delete only one explicit file path at a time; if bulk removal is needed, stop and ask the user to perform it manually.

写任何代码前必须完整阅读 `memory-bank/architecture.md`

写任何代码前必须完整阅读 `memory-bank/design-doc.md`、`memory-bank/tech-stack.md` 和 `memory-bank/implementation-plan.md`

每完成一个重大功能或里程碑后，必须更新 `memory-bank/architecture.md` 和 `memory-bank/progress.md`
