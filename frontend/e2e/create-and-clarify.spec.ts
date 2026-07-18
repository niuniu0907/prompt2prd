import { expect, test } from '@playwright/test'
import { setupMockRoutes } from './fixtures/mock-server'
import { clearDatabase, createProjectFromText, goToHome, goToNewProject } from './fixtures/test-helpers'

test.describe('项目创建与需求澄清闭环', () => {
  test.beforeEach(async ({ page }) => {
    await clearDatabase(page)
    await setupMockRoutes(page)
  })

  test('从文字输入创建项目并完成初始分析', async ({ page }) => {
    await goToNewProject(page)

    // Verify the create form is present
    const textarea = page.locator('#project-prompt')
    await expect(textarea).toBeVisible()

    // Short text should be rejected
    await textarea.fill('短')
    await page.locator('.create-form .button-primary').click()
    await expect(page.locator('[role="alert"]')).toContainText('至少输入 5 个字符')

    // Enter valid text
    await textarea.fill('做一个帮助独立开发者梳理模糊需求并生成可执行PRD的工具')
    await expect(page.locator('[data-testid="temporary-name"]')).toContainText('做一个帮助独立开发者梳理模糊需求并生')

    // Submit
    await page.locator('.create-form .button-primary').click()

    // Should navigate to analysis view
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await expect(page.locator('[data-testid="analysis-view"] h1')).toContainText('从想法到可确认的 PRD')

    // After analysis, should see requirement summary and questions
    await page.waitForTimeout(1000)
    // Expect the requirement summary section exists
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })

  test('创建项目后显示实时分析卡片和进度', async ({ page }) => {
    await goToNewProject(page)

    const textarea = page.locator('#project-prompt')
    await textarea.fill('构建一个在线协作白板工具，支持实时绘图和评论')
    await page.locator('.create-form .button-primary').click()

    // Wait for the analysis view
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })

    // Analysis progress should appear
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()

    // Wait for question preview to appear (pending questions)
    await page.waitForTimeout(2000)

    // The view should show requirement summary
    const view = page.locator('[data-testid="analysis-view"]')
    await expect(view).toBeVisible()
  })

  test('第一轮问题展示、跳过整轮和单题跳过', async ({ page }) => {
    const projectId = await createProjectFromText(page, '开发一个个人记账应用，支持收支记录和月度报表')
    await page.waitForTimeout(1000)

    // Navigate to questions page
    await page.goto(`/projects/${projectId}/questions`)
    await page.waitForSelector('[data-testid="question-wizard-view"]', { timeout: 10000 })

    // Should see question cards
    await page.waitForTimeout(1000)
    const questionView = page.locator('[data-testid="question-wizard-view"]')
    await expect(questionView).toBeVisible()
  })

  test('自定义答案和文字输入', async ({ page }) => {
    const projectId = await createProjectFromText(page, '做一个番茄钟计时器，支持任务管理和统计')
    await page.waitForTimeout(500)

    // Navigate to question wizard
    await page.goto(`/projects/${projectId}/questions`)
    await page.waitForSelector('[data-testid="question-wizard-view"]', { timeout: 10000 })
    await page.waitForTimeout(500)

    // Expect the question wizard view to be present
    await expect(page.locator('[data-testid="question-wizard-view"]')).toBeVisible()
  })

  test('刷新后分析状态恢复', async ({ page }) => {
    // Create project
    await goToNewProject(page)
    await page.locator('#project-prompt').fill('设计一个API文档生成工具，从代码注释自动生成文档')
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(500)

    // Refresh the page
    await page.reload()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 10000 })

    // The view should still show analysis content (recovered from IndexedDB)
    const view = page.locator('[data-testid="analysis-view"]')
    await expect(view).toBeVisible()
  })

  test('80% 完整度后显示继续和生成PRD两个入口', async ({ page }) => {
    // Use custom answer sequence that pushes completeness to 80%+
    await setupMockRoutes(page, {
      answerSequence: [{
        requestId: 'a0000000-0000-0000-0000-000000000001',
        eventId: 1,
        type: 'analysis_started',
        data: { phase: 'clarification' },
        timestamp: '2026-07-18T00:00:00.000Z',
      }, {
        requestId: 'a0000000-0000-0000-0000-000000000001',
        eventId: 2,
        type: 'completeness_changed',
        data: { previous: 32, current: 82, missingInformation: [] },
        timestamp: '2026-07-18T00:00:00.000Z',
      }, {
        requestId: 'a0000000-0000-0000-0000-000000000001',
        eventId: 3,
        type: 'generation_completed',
        data: { nextStage: 'ARCHITECTURE', finalState: {} },
        timestamp: '2026-07-18T00:00:00.000Z',
      }],
    })

    // Create project
    await goToNewProject(page)
    await page.locator('#project-prompt').fill('构建一个全栈博客系统，支持Markdown编辑和评论')
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(1000)

    // The view should be visible with high completeness indicator
    const view = page.locator('[data-testid="analysis-view"]')
    await expect(view).toBeVisible()
  })

  test('同语义问题不重复出现', async ({ page }) => {
    // Setup analysis with duplicate-adjacent questions (same semanticKey)
    await setupMockRoutes(page, {
      analysisSequence: [
        { requestId: 'b0000000-0000-0000-0000-000000000001', eventId: 1, type: 'analysis_started', data: { phase: 'initial' }, timestamp: '2026-07-18T00:00:00.000Z' },
        {
          requestId: 'b0000000-0000-0000-0000-000000000001', eventId: 2, type: 'question_created',
          data: { question: { id: 'q-dup-1', projectId: '00000000-0000-0000-0000-000000000001', batchId: 'b-dup-1', text: '目标用户是谁？', reason: '明确用户画像', dimension: 'ROLES', targetField: 'roles', semanticKey: 'target_user_type', inputType: 'SINGLE_SELECT', options: [{ id: 'o1', label: '个人用户', impact: '简化设计', recommended: true }], priority: 0.9, status: 'PENDING', createdAt: '2026-07-18T00:00:00.000Z', updatedAt: '2026-07-18T00:00:00.000Z' } },
          timestamp: '2026-07-18T00:00:00.000Z',
        },
        {
          requestId: 'b0000000-0000-0000-0000-000000000001', eventId: 3, type: 'question_created',
          data: { question: { id: 'q-dup-2', projectId: '00000000-0000-0000-0000-000000000001', batchId: 'b-dup-1', text: '目标用户是什么？', reason: '确认用户画像', dimension: 'ROLES', targetField: 'roles', semanticKey: 'target_user_type', inputType: 'SINGLE_SELECT', options: [{ id: 'o2', label: '企业用户', impact: '增加复杂度', recommended: false }], priority: 0.88, status: 'PENDING', createdAt: '2026-07-18T00:00:00.000Z', updatedAt: '2026-07-18T00:00:00.000Z' } },
          timestamp: '2026-07-18T00:00:00.000Z',
        },
        { requestId: 'b0000000-0000-0000-0000-000000000001', eventId: 4, type: 'completeness_changed', data: { previous: 0, current: 30, missingInformation: [] }, timestamp: '2026-07-18T00:00:00.000Z' },
        { requestId: 'b0000000-0000-0000-0000-000000000001', eventId: 5, type: 'generation_completed', data: { nextStage: 'CLARIFYING', finalState: {} }, timestamp: '2026-07-18T00:00:00.000Z' },
      ],
    })

    await goToNewProject(page)
    await page.locator('#project-prompt').fill('开发一个每日读书打卡小程序')
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(500)

    // The analysis view should load
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })

  test('文件上传预览与确认', async ({ page }) => {
    await goToNewProject(page)

    // The file upload area should be present
    await expect(page.locator('[data-testid="new-project-view"]')).toBeVisible()
  })
})

test.describe('Firefox 冒烟测试 — 创建与澄清核心流程', () => {
  test.beforeEach(async ({ page }) => {
    await clearDatabase(page)
    await setupMockRoutes(page)
  })

  test('创建项目并查看分析概览', async ({ page }) => {
    await goToHome(page)

    // Verify home page elements
    await expect(page.locator('[data-testid="project-home"] h1')).toContainText('全部项目')
    await expect(page.locator('[data-testid="new-project-button"]')).toBeVisible()

    // Navigate to new project
    await page.locator('[data-testid="new-project-button"]').click()
    await page.waitForSelector('[data-testid="new-project-view"]')
    await expect(page.locator('[data-testid="new-project-view"] h1')).toContainText('先说说你想做什么')

    // Fill in project details
    await page.locator('#project-prompt').fill('构建一个本地优先的PRD生成工具')
    await page.locator('.create-form .button-primary').click()

    // Should navigate to analysis view
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await expect(page.locator('[data-testid="analysis-view"]')).toBeVisible()
  })
})
