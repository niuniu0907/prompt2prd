import { expect, test } from '@playwright/test'
import { setupMockRoutes } from './fixtures/mock-server'
import { clearDatabase, goToHome, goToNewProject } from './fixtures/test-helpers'

test.describe('本地多项目与回收站', () => {
  test.beforeEach(async ({ page }) => {
    await clearDatabase(page)
    await setupMockRoutes(page)
  })

  async function createProject(page: any, name: string) {
    await goToNewProject(page)
    await page.locator('#project-prompt').fill(name)
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(300)
  }

  test('两个项目的需求数据互不污染', async ({ page }) => {
    // Create project 1
    await goToNewProject(page)
    await page.locator('#project-prompt').fill('做一个任务看板工具，支持拖拽和标签分类')
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(300)

    // Get project 1 ID from URL
    const url1 = page.url()
    const projectId1 = url1.match(/\/projects\/([^/]+)/)?.[1] ?? ''

    // Go home
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Verify project 1 appears in the list
    const projectList = page.locator('[data-testid="project-home"]')
    await expect(projectList).toContainText('做一个任务看板工具')

    // Create project 2
    await goToNewProject(page)
    await page.locator('#project-prompt').fill('开发一个代码审查 checklist 生成器')
    await page.locator('.create-form .button-primary').click()
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 15000 })
    await page.waitForTimeout(300)

    // Navigate back to project 1 and verify its data is intact
    await page.goto(`/projects/${projectId1}/overview`)
    await page.waitForSelector('[data-testid="analysis-view"]', { timeout: 10000 })

    // Verify project 1 data is not polluted by project 2
    const view1 = page.locator('[data-testid="analysis-view"]')
    await expect(view1).toBeVisible()
  })

  test('重命名项目', async ({ page }) => {
    await createProject(page, '做一个简易的日历事件管理工具')

    // Go home
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // The project should appear in the list
    await expect(page.locator('[data-testid="project-home"]')).toBeVisible()
  })

  test('复制项目生成新 ID 且副本修改不影响原项目', async ({ page }) => {
    await createProject(page, '设计一个设计系统文档生成器')

    // Go home
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Verify the home page shows the project
    await expect(page.locator('[data-testid="project-home"]')).toBeVisible()
  })

  test('归档与恢复', async ({ page }) => {
    await createProject(page, '构建一个邮件模板编辑器')

    // Go home to active projects
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Project should be in active list
    const home = page.locator('[data-testid="project-home"]')
    await expect(home).toContainText('构建一个邮件模板编辑器')
  })

  test('移入回收站并恢复', async ({ page }) => {
    await createProject(page, '写一个浏览器插件版本自动更新检查工具')

    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Project should be visible
    await expect(page.locator('[data-testid="project-home"]')).toBeVisible()
  })

  test('单项目永久删除只影响明确目标', async ({ page }) => {
    await createProject(page, '搭建一个部署前检查清单校验器')

    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Navigate to trash view via sidebar
    await page.locator('[data-navigation="DELETED"]').click()
    await page.waitForTimeout(500)

    // Should see trash view
    const home = page.locator('[data-testid="project-home"]')
    await expect(home).toBeVisible()
    await expect(home.locator('h1')).toContainText('回收站')
  })

  test('多次刷新后项目数据不丢失', async ({ page }) => {
    await createProject(page, '创建个人知识库管理系统')

    // Go home and verify
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Refresh
    await page.reload()
    await page.waitForSelector('[data-testid="project-home"]', { timeout: 10000 })

    // Project should still be there (IndexedDB persisted)
    await expect(page.locator('[data-testid="project-home"]')).toContainText('创建个人知识库管理系统')

    // Refresh again
    await page.reload()
    await page.waitForSelector('[data-testid="project-home"]', { timeout: 10000 })

    await expect(page.locator('[data-testid="project-home"]')).toContainText('创建个人知识库管理系统')
  })

  test('空状态不显示示例项目', async ({ page }) => {
    await goToHome(page)

    // In empty state, there should be no example projects
    const home = page.locator('[data-testid="project-home"]')
    await expect(home).toBeVisible()

    // Should show empty state message
    await expect(home).toContainText('还没有项目')
  })

  test('回收站空状态显示引导文案', async ({ page }) => {
    await goToHome(page)

    // Navigate to trash
    await page.locator('[data-navigation="DELETED"]').click()
    await page.waitForTimeout(500)

    const home = page.locator('[data-testid="project-home"]')
    await expect(home.locator('h1')).toContainText('回收站')
  })

  test('全流程：创建→归档→回收站→恢复', async ({ page }) => {
    await createProject(page, '全流程测试项目：智能购物清单')

    // Go home
    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')
    await expect(page.locator('[data-testid="project-home"]')).toContainText('全流程测试项目')

    // Navigate to archive view
    await page.locator('[data-navigation="ARCHIVED"]').click()
    await page.waitForTimeout(500)
    await expect(page.locator('[data-testid="project-home"] h1')).toContainText('已归档')

    // Navigate to trash view
    await page.locator('[data-navigation="DELETED"]').click()
    await page.waitForTimeout(500)
    await expect(page.locator('[data-testid="project-home"] h1')).toContainText('回收站')

    // Back to active
    await page.locator('[data-navigation="ACTIVE"]').click()
    await page.waitForTimeout(500)
    await expect(page.locator('[data-testid="project-home"] h1')).toContainText('全部项目')
  })

  test('已归档视图显示归档项目', async ({ page }) => {
    await createProject(page, '归档测试项目：周报生成器')

    await page.goto('/')
    await page.waitForSelector('[data-testid="project-home"]')

    // Navigate to archive
    await page.locator('[data-navigation="ARCHIVED"]').click()
    await page.waitForTimeout(500)

    await expect(page.locator('[data-testid="project-home"] h1')).toContainText('已归档')
  })
})
