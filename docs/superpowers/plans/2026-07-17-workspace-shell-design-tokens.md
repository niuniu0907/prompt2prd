# Workspace Shell and Design Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the step-5 light desktop workspace shell, empty project home route, and exact Prompt2PRD design tokens with component and browser-level visual verification.

**Architecture:** `App.vue` becomes a pure route outlet. `AppShell.vue` owns stable global navigation, while `ProjectHomeView.vue` owns only the empty project-home canvas; later project data features can replace its static empty state without restructuring the shell.

**Tech Stack:** Vue 3 SFCs, Vue Router, CSS custom properties, Vitest, Vue Test Utils, Playwright CLI.

## Global Constraints

- Implement only `memory-bank/implementation-plan.md` step 5.
- Use the exact documented colors: primary `#c7eb64`, accent `#249da5`, background `#eff3f4`, surface `#ffffff`, primary text `#262b25`, secondary text `#626d72`, muted text `#80898f`, and accent text `#111713`.
- Yellow-green controls must use dark `#262b25` text; normal helper text must use `#626d72`.
- Provide only a light theme; do not add a theme switch.
- Create an empty route and layout only; do not add project persistence, sample projects, filters, forms, or working navigation actions.
- Use Airtable-style structured spacing, borders, and restrained shadows without copying Airtable colors, assets, or branding.

---

### Task 1: Define the visual foundation and stable shell

**Files:**
- Create: `frontend/src/styles/tokens.css`
- Create: `frontend/src/layouts/AppShell.vue`

**Interfaces:**
- Consumes: the design-document token values and a default content slot.
- Produces: global CSS variables, base element styles, reusable primary button styling, and four visible global navigation entries.

- [ ] **Step 1: Define exact tokens and base styles**

Declare the eight required color variables on `:root`, add neutral border/radius/shadow tokens, set light-only `color-scheme`, and establish a system sans-serif stack.

- [ ] **Step 2: Build the sidebar shell**

Render Prompt2PRD branding, “全部项目 / 已归档 / 回收站 / 模型设置” entries, a local-first status note, and the main-content slot. Mark only “全部项目” as current; other entries remain inert in this shell step.

- [ ] **Step 3: Keep desktop layout resilient**

Use a fixed 240-pixel-class sidebar and flexible main canvas, with a compact desktop fallback below 1080 pixels. Do not implement a mobile navigation mode.

### Task 2: Add the empty project route

**Files:**
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/views/ProjectHomeView.vue`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`

**Interfaces:**
- Consumes: `AppShell.vue`, Vue Router history, and the global token stylesheet.
- Produces: route `/`, page header, two inert create-project controls, empty state, and local-data notice.

- [ ] **Step 1: Create a testable router factory**

Export `createAppRouter(history)` with one `/` route named `project-home`, plus the browser-history singleton used by `main.ts`.

- [ ] **Step 2: Build the empty project home**

Render “全部项目”, “新建项目”, “最近项目”, a no-project empty state, and the browser-local data warning. Do not render sample project content.

- [ ] **Step 3: Connect the application**

Import global tokens in `main.ts`, install the exported router, and make `App.vue` contain only `RouterView`.

### Task 3: Verify component contracts

**Files:**
- Create: `frontend/src/layouts/AppShell.spec.ts`
- Modify: `frontend/src/App.spec.ts`

**Interfaces:**
- Consumes: the router factory, CSS custom properties, shell, and home view.
- Produces: regression checks for route mounting, navigation copy, primary colors, empty state, and absence of dark-theme controls.

- [ ] **Step 1: Test navigation and theme constraints**

Assert all four navigation entries exist, only one is current, the eight required token values are present, the reusable primary-button rule uses primary background plus primary-text foreground, and no theme-switch copy or control exists.

- [ ] **Step 2: Test the routed application**

Mount `App` with memory history and Pinia, navigate to `/`, and assert the page title, empty state, create controls, Router, and Pinia are present.

- [ ] **Step 3: Run tests and build**

Run `npm --prefix frontend run test` and `npm --prefix frontend run build`; both must succeed.

### Task 4: Perform visual browser verification

**Files:**
- Create: `output/playwright/step-5-workspace-1280.png`

**Interfaces:**
- Consumes: the Vite development server on loopback.
- Produces: a 1280-pixel-wide screenshot and browser evidence for exact computed colors and layout visibility.

- [ ] **Step 1: Start Vite and open the page**

Start the frontend dev server in a hidden process, open it with Playwright CLI, and take a semantic snapshot before interaction.

- [ ] **Step 2: Resize and inspect**

Resize to `1280 800`, verify the sidebar, page header, primary action, empty state, and local-data notice are visible without horizontal overflow.

- [ ] **Step 3: Verify computed colors and capture**

Confirm the primary button computes to `rgb(199, 235, 100)` with `rgb(38, 43, 37)` text, then save `output/playwright/step-5-workspace-1280.png` and close the Playwright session.

### Task 5: Record verified milestone state

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

**Interfaces:**
- Consumes: passing tests/build and the inspected screenshot.
- Produces: verified current-state documentation pointing to step 6.

- [ ] **Step 1: Update architecture**

Record the route/shell/view boundary, token source, and light-only visual constraints.

- [ ] **Step 2: Update progress**

Record automated and visual evidence, known inert controls, and step 6 as the next action.
