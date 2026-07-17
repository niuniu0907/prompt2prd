# Vue Frontend Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the step-3 Vue frontend project with strict TypeScript, Router and Pinia initialization, on-demand Element Plus imports, and a verified production build.

**Architecture:** `frontend` remains an independent npm/Vite project during this step. `main.ts` owns browser startup and installs one empty router plus one Pinia instance; `App.vue` is intentionally a minimal root because the workspace shell belongs to step 5.

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Element Plus, Dexie, markdown-it, Mermaid, Vitest, Vue Test Utils, jsdom.

## Global Constraints

- Implement only `memory-bank/implementation-plan.md` step 3.
- Use TypeScript strict mode and commit `package-lock.json`.
- Configure Element Plus component and API imports through Vite plugins; do not globally install the complete UI library.
- Do not implement workspace navigation, design tokens, persistence schemas, APIs, or Maven frontend packaging in this step.

---

### Task 1: Create the package and compiler/build configuration

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.app.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/env.d.ts`

**Interfaces:**
- Consumes: Node.js LTS and npm.
- Produces: `dev`, `build`, `test`, and `typecheck` npm scripts plus strict Vue SFC compilation.

- [ ] **Step 1: Define scripts and an empty dependency manifest**

Create scripts where `build` runs type checking before `vite build`, `test` runs Vitest once, and `typecheck` checks both application and Vite configuration files.

- [ ] **Step 2: Configure strict TypeScript**

Set `strict: true`, `moduleResolution: Bundler`, `isolatedModules: true`, `noEmit: true`, and browser ES2022 libraries for application code. Use a separate Node-oriented configuration for `vite.config.ts`.

- [ ] **Step 3: Configure Vite, Vitest, and on-demand imports**

Use the Vue plugin, `unplugin-auto-import`, `unplugin-vue-components`, and `ElementPlusResolver`. Configure jsdom and Vue Test Utils setup through the single Vite/Vitest configuration.

### Task 2: Add the minimal app and initialization test

**Files:**
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/App.spec.ts`

**Interfaces:**
- Consumes: Vue, Router, and Pinia plugin APIs.
- Produces: a mounted `#app` application and a smoke test proving both plugins are installed.

- [ ] **Step 1: Write the smoke test**

Mount `App.vue` with `createMemoryHistory()`, an empty route list, and `createPinia()`. Assert the root renders and `$router`/`$pinia` are the exact installed instances.

- [ ] **Step 2: Add the minimal root component**

Render only a semantic root containing the product name. Do not add the step-5 shell, navigation, colors, or design tokens.

- [ ] **Step 3: Add browser startup**

Create an empty HTML-history router, create Pinia, install both plugins, and mount `App` into `#app`.

### Task 3: Lock dependencies and verify the frontend

**Files:**
- Create: `frontend/package-lock.json`
- Generated: `frontend/src/auto-imports.d.ts`
- Generated: `frontend/src/components.d.ts`

**Interfaces:**
- Consumes: the npm registry.
- Produces: reproducible dependency resolution and generated on-demand import declarations.

- [ ] **Step 1: Install runtime dependencies exactly**

Install Vue, Vue Router, Pinia, Element Plus, Dexie, markdown-it, and Mermaid with `--save-exact`.

- [ ] **Step 2: Install development dependencies exactly**

Install TypeScript, Vite, Vue Vite plugin, Vue Test Utils, Vitest, jsdom, vue-tsc, Node types, markdown-it types, and both unplugin packages with `--save-dev --save-exact`.

- [ ] **Step 3: Run tests**

Run: `npm --prefix frontend run test`

Expected: the app smoke test passes and confirms Router plus Pinia initialization.

- [ ] **Step 4: Run the production build**

Run: `npm --prefix frontend run build`

Expected: strict type checks pass and Vite writes a production bundle under `frontend/dist`.

### Task 4: Record verified milestone state

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: successful test and build output.
- Produces: current-state documentation pointing to step 4.

- [ ] **Step 1: Update architecture**

Record the independent Vite frontend boundary, initialization chain, strict compiler settings, and on-demand Element Plus setup.

- [ ] **Step 2: Update progress**

Record exact test/build results and mark step 4 as next only after both commands succeed.

- [ ] **Step 3: Review scope and files**

Run `git status --short`, inspect `frontend/package.json`, and confirm no step-4 Maven edits or step-5 UI implementation were added.
