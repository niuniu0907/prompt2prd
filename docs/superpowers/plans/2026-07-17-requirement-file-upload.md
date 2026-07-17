# Requirement File Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe Markdown/TXT selection, UTF-8 parsing, local preview, first-use privacy confirmation, complete Unicode-aware chunking, sequential progress, cancellation, and integration with project creation.

**Architecture:** Keep file bytes and preview work in the browser. `fileParser.ts` owns deterministic validation, decoding, semantic chunking, and sequential processing; `RequirementFileUpload.vue` owns the user workflow and persists only the privacy acknowledgement through the existing `app_setting` table. `NewProjectView.vue` holds the confirmed file in route-local state and passes it to the existing project form, which remains the only component that creates a project.

**Tech Stack:** Vue 3 Composition API, TypeScript strict mode, Dexie/IndexedDB, Vitest, Vue Test Utils, fake-indexeddb.

## Global Constraints

- Accept only `.md` and `.txt` files encoded as valid UTF-8.
- Reject files larger than 2 MiB without truncating them.
- Reject empty or whitespace-only decoded content.
- Split on Markdown heading and paragraph boundaries where possible; hard-split oversized blocks so every chunk is at most 32,000 Unicode code points.
- Preserve all normalized decoded content: joining chunks must reproduce the parser's content exactly.
- Show the exact third-party model privacy warning before first use and persist acknowledgement only in `app_setting`.
- Never persist API keys, credentials, or file bytes outside the existing project record.
- Do not claim that local chunk preparation is a real model analysis call.

---

### Task 1: Deterministic UTF-8 parser and chunk processor

**Files:**
- Create: `frontend/src/features/projects/fileParser.ts`
- Test: `frontend/src/features/projects/fileParser.spec.ts`

**Interfaces:**
- Produces: `parseRequirementFile(file): Promise<ParsedRequirementFile>`
- Produces: `splitRequirementText(content, maxCharacters?): string[]`
- Produces: `processChunksSequentially(chunks, handler, signal?): Promise<void>`

- [ ] **Step 1: Write failing parser tests**

Test valid Markdown/TXT, case-insensitive extensions, empty input, invalid extension, invalid UTF-8 bytes, `2 * 1024 * 1024 + 1` bytes, exact Unicode boundaries with emoji, preservation via `chunks.join('')`, every chunk length, sequential callback order, and an aborted signal that prevents later callbacks.

- [ ] **Step 2: Run the focused test and confirm missing-module failure**

Run: `npm --prefix frontend run test -- fileParser.spec.ts`

Expected: FAIL because `./fileParser` does not exist.

- [ ] **Step 3: Implement the parser and processor**

Use these public contracts:

```ts
export const MAX_REQUIREMENT_FILE_BYTES = 2 * 1024 * 1024
export const MAX_REQUIREMENT_CHUNK_CHARACTERS = 32_000

export interface ParsedRequirementFile {
  name: string
  content: string
  chunks: string[]
}

export class RequirementFileError extends Error {
  constructor(public readonly code: 'TYPE' | 'SIZE' | 'ENCODING' | 'EMPTY', message: string) {
    super(message)
  }
}

export async function parseRequirementFile(file: File): Promise<ParsedRequirementFile>
export function splitRequirementText(content: string, maxCharacters?: number): string[]
export async function processChunksSequentially(
  chunks: readonly string[],
  handler: (chunk: string, index: number, total: number) => void | Promise<void>,
  signal?: AbortSignal,
): Promise<void>
```

Decode with `new TextDecoder('utf-8', { fatal: true })`, remove a leading BOM, normalize CRLF/CR to LF, and use `Array.from()` for Unicode code-point counts and hard splitting. Throw `DOMException('The operation was aborted.', 'AbortError')` before each callback when cancelled.

- [ ] **Step 4: Run focused tests**

Run: `npm --prefix frontend run test -- fileParser.spec.ts`

Expected: PASS.

### Task 2: Upload, privacy, preview, progress, and cancellation component

**Files:**
- Create: `frontend/src/features/projects/RequirementFileUpload.vue`
- Test: `frontend/src/features/projects/RequirementFileUpload.spec.ts`

**Interfaces:**
- Consumes: parser interfaces from Task 1 and `appDatabase.app_setting`.
- Produces event: `confirmed(file: ParsedRequirementFile)`.
- Produces event: `cleared()`.
- Accepts optional test/future-analysis injection: `privacySetting` and `processChunk`.

- [ ] **Step 1: Write failing component tests**

Cover valid file preview before confirmation, exact privacy warning on first use, acknowledgement persistence, no repeated modal for an accepted setting, always-visible short reminder, parse error messages, sequential progress, final confirmed event, cancellation without confirmed output, and disabled controls while processing.

- [ ] **Step 2: Run focused tests and confirm missing-component failure**

Run: `npm --prefix frontend run test -- RequirementFileUpload.spec.ts`

Expected: FAIL because the component does not exist.

- [ ] **Step 3: Implement the component**

The component must expose a native input with `accept=".md,.txt,text/markdown,text/plain"`, render decoded content only in a readonly textarea, display file byte and Unicode-character counts, and use explicit buttons for privacy acceptance, file confirmation, cancellation, replacement, and clearing. The default privacy adapter reads/writes this exact record:

```ts
{
  key: 'uploadPrivacyNoticeAccepted',
  value: true,
  updatedAt: new Date().toISOString(),
}
```

On file confirmation, run `processChunksSequentially()` with the injected handler, update `processedChunks / totalChunks`, and emit only after every chunk succeeds. Aborts return to preview without emitting. Other failures show an actionable error and retain the preview.

- [ ] **Step 4: Run focused tests**

Run: `npm --prefix frontend run test -- RequirementFileUpload.spec.ts`

Expected: PASS.

### Task 3: Connect confirmed files to project creation

**Files:**
- Modify: `frontend/src/views/NewProjectView.vue`
- Modify: `frontend/src/views/NewProjectView.spec.ts`
- Modify: `frontend/src/features/projects/ProjectCreateForm.vue`
- Modify: `frontend/src/features/projects/ProjectCreateForm.spec.ts`

**Interfaces:**
- Consumes: `ParsedRequirementFile` from Task 1.
- Passes: `{ name, content, chunks }` as the confirmed file UI state.
- Persists through existing `CreateProjectInput`: `uploadedFileName`, `uploadedFileContent`, and optional `supplementalPrompt`.

- [ ] **Step 1: Add failing integration tests**

Mount the new-project route with an accepted privacy adapter, select a Markdown file, verify preview, confirm it, submit with no text, and assert repository creation receives the full file name/content. Add a replacement/clear assertion proving cleared file state restores the five-character text rule.

- [ ] **Step 2: Run view and form tests and confirm failure**

Run: `npm --prefix frontend run test -- NewProjectView.spec.ts ProjectCreateForm.spec.ts`

Expected: FAIL because the upload component is not wired into the view.

- [ ] **Step 3: Implement integration**

Hold `const confirmedFile = ref<ParsedRequirementFile | null>(null)` in `NewProjectView.vue`, render `RequirementFileUpload` before `ProjectCreateForm`, pass `:file-input="confirmedFile"`, and clear the ref on `cleared`. Extend `ConfirmedProjectFile` with `chunks: string[]`; do not add chunks to the persisted project schema because the raw confirmed text remains the source for future analysis and can be deterministically re-split.

- [ ] **Step 4: Run focused integration tests**

Run: `npm --prefix frontend run test -- NewProjectView.spec.ts ProjectCreateForm.spec.ts`

Expected: PASS.

### Task 4: Verify milestone and update current-state documentation

**Files:**
- Modify: `memory-bank/architecture.md`
- Modify: `memory-bank/progress.md`

- [ ] **Step 1: Run complete verification**

Run: `npm --prefix frontend run test`

Expected: all Vitest tests PASS.

Run: `npm --prefix frontend run typecheck`

Expected: strict Vue and TypeScript checks PASS.

Run: `npm --prefix frontend run build`

Expected: Vite production build PASS.

Run: `.\mvnw.cmd test`

Expected: root and backend Maven modules PASS.

- [ ] **Step 2: Update architecture and progress with verified facts only**

Record parser limits, UTF-8 failure behavior, local privacy-setting key, safe readonly preview, deterministic chunk preservation, sequential preparation/cancellation boundary, integration with project creation, exact test counts, and the fact that real AI calls remain unimplemented until later steps.

- [ ] **Step 3: Review repository status**

Run: `git status --short`

Expected: only intentional repository files and existing untracked project baseline are present; no generated private documents, API keys, or browser data were added.
