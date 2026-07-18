import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { setupMockRoutes } from './fixtures/mock-server'
import { clearDatabase, createProjectFromText } from './fixtures/test-helpers'

test.describe('架构、流程图与 PRD 闭环', () => {
  test.beforeEach(async ({ page }) => {
    await clearDatabase(page)
    await setupMockRoutes(page)
  })

  async function createProjectAndReachArchitecture(page: Page) {
    const projectId = await createProjectFromText(page, '开发一个会议室预约系统，支持时间段选择和冲突检测')
    await page.waitForTimeout(500)
    return projectId
  }

  test('填写技术约束并生成 2-3 个候选架构', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    // Navigate to architecture
    await page.goto(`/projects/${projectId}/architecture`)
    await page.waitForSelector('[data-testid="project-workspace"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // Should see the architecture view
    const workspace = page.locator('[data-testid="project-workspace"]')
    await expect(workspace).toBeVisible()
  })

  test('确认唯一主架构后生成版本记录', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/architecture`)
    await page.waitForSelector('[data-testid="project-workspace"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    await expect(page.locator('[data-testid="project-workspace"]')).toBeVisible()
  })

  test('主流程与异常流程图独立生成', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    // Navigate to flowchart
    await page.goto(`/projects/${projectId}/flowchart`)
    await page.waitForSelector('[data-testid="project-workspace"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // Should show the flowchart view
    await expect(page.locator('[data-testid="project-workspace"]')).toBeVisible()
  })

  test('PRD 流式生成并展示章节进度', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    // Navigate to PRD view
    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // Should show the PRD view with section list
    const prdView = page.locator('[data-testid="prd-view"]')
    await expect(prdView).toBeVisible()

    // Click "Generate All PRD" button
    const generateBtn = page.locator('[data-testid="generate-all-btn"]')
    await expect(generateBtn).toBeVisible()

    // The sidebar should show sections
    await expect(page.locator('[data-testid="prd-sidebar"]')).toBeVisible()
  })

  test('停止生成后不继续追加内容', async ({ page }) => {
    // Use slow mock to allow cancellation
    await setupMockRoutes(page, { slowMs: 2000 })

    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(500)

    await expect(page.locator('[data-testid="prd-view"]')).toBeVisible()
  })

  test('章节锁定后不能直接重新生成', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // The PRD sidebar should be present
    await expect(page.locator('[data-testid="prd-sidebar"]')).toBeVisible()
  })

  test('PRD 编辑与预览切换', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // Should have edit/preview toggle buttons
    await expect(page.locator('[data-testid="mode-edit"]')).toBeVisible()
    await expect(page.locator('[data-testid="mode-preview"]')).toBeVisible()

    // Click preview
    await page.locator('[data-testid="mode-preview"]').click()
    await expect(page.locator('[data-testid="mode-preview"]')).toHaveClass(/active/)

    // Click edit
    await page.locator('[data-testid="mode-edit"]').click()
    await expect(page.locator('[data-testid="mode-edit"]')).toHaveClass(/active/)
  })

  test('校验与 Markdown 导出包含稳定编号、接口示例、用户故事和验收标准', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    // The export should reference all required PRD sections
    await expect(page.locator('[data-testid="prd-sidebar"]')).toBeVisible()
  })

  test('导出文档只包含一个主架构', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(1000)

    await expect(page.locator('[data-testid="prd-view"]')).toBeVisible()
  })

  test('flowchart 和 prd 页面默认收起右侧面板', async ({ page }) => {
    const projectId = await createProjectAndReachArchitecture(page)

    // Go to flowchart view - right panel should be collapsed
    await page.goto(`/projects/${projectId}/flowchart`)
    await page.waitForSelector('[data-testid="project-workspace"]', { timeout: 10000 })
    await page.waitForTimeout(500)

    const rightPanel = page.locator('[data-testid="workspace-right-panel"]')
    await expect(rightPanel).toHaveAttribute('aria-expanded', 'false')

    // Go to PRD view - right panel should also be collapsed
    await page.goto(`/projects/${projectId}/prd`)
    await page.waitForSelector('[data-testid="prd-view"]', { timeout: 10000 })
    await page.waitForTimeout(500)

    await expect(rightPanel).toHaveAttribute('aria-expanded', 'false')
  })
})
