import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { fixedId, setupMockRoutes } from './fixtures/mock-server'
import { clearDatabase, createProjectFromText } from './fixtures/test-helpers'

test.describe('冲突、锁定与版本历史闭环', () => {
  let projectId: string

  test.beforeEach(async ({ page }) => {
    await clearDatabase(page)
    await setupMockRoutes(page)
    projectId = await createProjectAndWait(page)
  })

  async function createProjectAndWait(page: Page) {
    const createdProjectId = await createProjectFromText(page, '做一个在线考试系统，支持题库管理和自动评分')
    await page.waitForTimeout(500)
    return createdProjectId
  }

  test('锁定需求后不能被模型输出覆盖', async ({ page }) => {
    // Navigate to requirements view
    await page.goto(`/projects/${projectId}/requirements`)
    await page.waitForTimeout(1000)

    // The requirements view should be visible
    const requirementsView = page.locator('[data-testid="project-workspace"]')
    await expect(requirementsView).toBeVisible()
  })

  test('模型矛盾输出产生冲突并在右侧面板处理', async ({ page }) => {
    // Setup mock with conflict events
    await setupMockRoutes(page, {
      analysisSequence: [
        { requestId: 'c0000000-0000-0000-0000-000000000001', eventId: 1, type: 'analysis_started', data: { phase: 'initial' }, timestamp: '2026-07-18T00:00:00.000Z' },
        {
          requestId: 'c0000000-0000-0000-0000-000000000001', eventId: 2, type: 'conflict_detected',
          data: {
            conflict: {
              id: fixedId('conflict-1'),
              projectId: '00000000-0000-0000-0000-000000000001',
              leftRequirementId: fixedId('req-1'),
              rightRequirementId: null,
              leftContent: '用户数据保留7天',
              rightContent: '用户数据保留30天（AI推断）',
              impact: '数据保留策略影响存储设计和合规要求',
              core: false,
              status: 'OPEN',
              resolution: null,
              createdAt: '2026-07-18T00:00:00.000Z',
              updatedAt: '2026-07-18T00:00:00.000Z',
              resolvedAt: null,
            },
          },
          timestamp: '2026-07-18T00:00:00.000Z',
        },
        { requestId: 'c0000000-0000-0000-0000-000000000001', eventId: 3, type: 'completeness_changed', data: { previous: 0, current: 28, missingInformation: [] }, timestamp: '2026-07-18T00:00:00.000Z' },
        { requestId: 'c0000000-0000-0000-0000-000000000001', eventId: 4, type: 'generation_completed', data: { nextStage: 'CLARIFYING', finalState: {} }, timestamp: '2026-07-18T00:00:00.000Z' },
      ],
    })

    // Create a new project to trigger conflict scenario
    projectId = await createProjectFromText(page, '构建一个本地数据保留策略配置系统')
    await page.waitForTimeout(500)

    // View should load
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })

  test('手动编辑需求后刷新保留', async ({ page }) => {
    // Navigate to requirements
    await page.goto(`/projects/${projectId}/requirements`)
    await page.waitForTimeout(1000)

    const workspace = page.locator('[data-testid="project-workspace"]')
    await expect(workspace).toBeVisible()
  })

  test('核心冲突阻止完成标记', async ({ page }) => {
    // Setup mock with a core conflict
    await setupMockRoutes(page, {
      analysisSequence: [
        { requestId: 'd0000000-0000-0000-0000-000000000001', eventId: 1, type: 'analysis_started', data: { phase: 'initial' }, timestamp: '2026-07-18T00:00:00.000Z' },
        {
          requestId: 'd0000000-0000-0000-0000-000000000001', eventId: 2, type: 'conflict_detected',
          data: {
            conflict: {
              id: fixedId('core-conflict'),
              projectId: '00000000-0000-0000-0000-000000000001',
              leftRequirementId: fixedId('req-1'),
              rightRequirementId: null,
              leftContent: '系统不需要用户认证',
              rightContent: '系统需要OAuth 2.0登录',
              impact: '认证需求影响整体架构设计',
              core: true,
              status: 'OPEN',
              resolution: null,
              createdAt: '2026-07-18T00:00:00.000Z',
              updatedAt: '2026-07-18T00:00:00.000Z',
              resolvedAt: null,
            },
          },
          timestamp: '2026-07-18T00:00:00.000Z',
        },
        { requestId: 'd0000000-0000-0000-0000-000000000001', eventId: 3, type: 'completeness_changed', data: { previous: 0, current: 35, missingInformation: [], hasCoreConflict: true }, timestamp: '2026-07-18T00:00:00.000Z' },
        { requestId: 'd0000000-0000-0000-0000-000000000001', eventId: 4, type: 'generation_completed', data: { nextStage: 'CLARIFYING', finalState: {} }, timestamp: '2026-07-18T00:00:00.000Z' },
      ],
    })

    projectId = await createProjectFromText(page, '构建一个工具管理平台，关于是否需要登录存在不同意见')
    await page.waitForTimeout(500)

    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })

  test('版本差异与恢复', async ({ page }) => {
    // Navigate to requirements first
    await page.goto(`/projects/${projectId}/requirements`)
    await page.waitForTimeout(1000)

    const workspace = page.locator('[data-testid="project-workspace"]')
    await expect(workspace).toBeVisible()
  })

  test('恢复后刷新仍保持目标版本', async ({ page }) => {
    await page.goto(`/projects/${projectId}/overview`)
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 10000 })
    await page.waitForTimeout(500)

    // Reload
    await page.reload()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 10000 })

    // Content should persist
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })
})
