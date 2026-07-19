import type { Page } from '@playwright/test'

/**
 * Navigate to the project home page and wait for it to be fully loaded.
 */
export async function goToHome(page: Page): Promise<void> {
  await page.goto('/')
  await page.waitForSelector('[data-testid="project-home"]')
}

/**
 * Navigate to the new project page.
 */
export async function goToNewProject(page: Page): Promise<void> {
  await page.goto('/projects/new')
  await page.waitForSelector('[data-testid="new-project-view"]')
}

/**
 * Create a project by typing a prompt and clicking submit.
 * Returns once the page navigates to the project overview.
 */
export async function createProjectFromText(
  page: Page,
  prompt: string,
): Promise<string> {
  await goToNewProject(page)

  const textarea = page.locator('#project-prompt')
  await textarea.fill(prompt)

  await page.locator('.create-form .button-primary').click()

  // Wait for navigation to project workspace
  await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
  return currentProjectId(page)
}

/**
 * Navigate to a project by its ID.
 */
export async function goToProject(page: Page, projectId: string): Promise<void> {
  await page.goto(`/projects/${projectId}/overview`)
  await page.waitForSelector('[data-testid="project-workspace"]')
}

/**
 * Clear all IndexedDB data in the page.
 * Navigates to the app first to ensure an origin is established.
 */
export async function clearDatabase(page: Page): Promise<void> {
  // Navigate to the app first to establish a secure origin
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  await page.evaluate(async () => {
    const names = ['prompt2prd']
    for (const name of names) {
      indexedDB.deleteDatabase(name)
    }
    try {
      const dbs = await indexedDB.databases?.() ?? []
      for (const db of dbs) {
        if (db.name && !names.includes(db.name)) {
          indexedDB.deleteDatabase(db.name)
        }
      }
    } catch {
      // databases() may not be available
    }
  })
}

/**
 * Wait for the analysis view to show a completion state.
 */
export async function waitForAnalysisComplete(page: Page): Promise<void> {
  // Wait until the analysis is done (progress at 100 or questions appear)
  await page.waitForFunction(() => {
    const progressEl = document.querySelector('[data-testid="analysis-view"]')
    return progressEl && !progressEl.textContent?.includes('正在连接分析服务')
  }, { timeout: 15000 })
}

/**
 * Get text content of an element by testid.
 */
export async function textByTestId(page: Page, testId: string): Promise<string> {
  const el = page.locator(`[data-testid="${testId}"]`)
  return (await el.textContent()) ?? ''
}

export function currentProjectId(page: Page): string {
  const projectId = page.url().match(/\/projects\/([^/]+)/)?.[1]
  if (!projectId) throw new Error(`Cannot read project id from URL: ${page.url()}`)
  return projectId
}
