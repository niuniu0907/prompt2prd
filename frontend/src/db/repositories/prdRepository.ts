import type { AppDatabase } from '@/db/appDatabase'
import { appDatabase } from '@/db/appDatabase'
import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import { PRD_SECTION_KEYS, type PrdSection, type PrdSectionKey, type PrdSectionStatus } from '@/features/prd/types'
import type { RequirementStateSnapshot } from '@/features/requirements/types'

export class PrdRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async listByProject(projectId: string): Promise<PrdSection[]> {
    assertUuid(projectId, 'project id')
    const records = await this.database.prd_section.where('projectId').equals(projectId).toArray()
    return records.sort((a, b) => a.order - b.order)
  }

  async getByKey(projectId: string, sectionKey: string): Promise<PrdSection | undefined> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    return this.database.prd_section.where('[projectId+sectionKey]').equals([projectId, sectionKey.trim()]).first()
  }

  async initializeSections(projectId: string, now = new Date().toISOString()): Promise<PrdSection[]> {
    assertUuid(projectId, 'project id')
    assertUtcIsoDateTime(now, 'prd timestamp')
    const existing = await this.listByProject(projectId)
    if (existing.length > 0) return existing

    const sections: PrdSection[] = PRD_SECTION_KEYS.map((key, index) => ({
      id: this.nextId(),
      projectId,
      sectionKey: key,
      title: sectionTitle(key),
      content: '',
      order: index + 1,
      status: 'DRAFT' as PrdSectionStatus,
      locked: false,
      errorCode: null,
      createdAt: now,
      updatedAt: now,
    }))

    await this.database.transaction('rw', this.tables(), async () => {
      for (const section of sections) {
        await this.database.prd_section.add(section)
      }
    })
    return sections
  }

  async updateContent(
    projectId: string,
    sectionKey: string,
    content: string,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      if (section.locked) throw new Error(`Section ${section.title} is locked`)
      const updated: PrdSection = { ...section, content, updatedAt: now }
      await this.database.prd_section.put(updated)
      return updated
    })
  }

  async updateStatus(
    projectId: string,
    sectionKey: string,
    status: PrdSectionStatus,
    errorCode: string | null = null,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      const updated: PrdSection = { ...section, status, errorCode, updatedAt: now }
      await this.database.prd_section.put(updated)
      return updated
    })
  }

  async lockSection(
    projectId: string,
    sectionKey: string,
    locked: boolean,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      const updated: PrdSection = { ...section, locked, updatedAt: now }
      await this.database.prd_section.put(updated)
      return updated
    })
  }

  async saveSection(
    projectId: string,
    sectionKey: string,
    content: string,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      const updated: PrdSection = {
        ...section, content, status: 'COMPLETED' as PrdSectionStatus, updatedAt: now,
      }
      await this.database.prd_section.put(updated)
      await this.recordVersion(projectId, `编辑 PRD 章节：${section.title}`, now)
      return updated
    })
  }

  async saveGeneratedContent(
    projectId: string,
    sectionKey: string,
    content: string,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      if (section.locked) return section
      const updated: PrdSection = {
        ...section, content, status: 'COMPLETED' as PrdSectionStatus, errorCode: null, updatedAt: now,
      }
      await this.database.prd_section.put(updated)
      return updated
    })
  }

  async saveBeforeRegeneration(
    projectId: string,
    sectionKey: string,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      if (section.locked) throw new Error(`Cannot regenerate locked section: ${section.title}`)
      await this.recordVersion(projectId, `重新生成 PRD 章节前备份：${section.title}`, now)
      return section
    })
  }

  async replaceAfterRegeneration(
    projectId: string,
    sectionKey: string,
    content: string,
    now = new Date().toISOString(),
  ): Promise<PrdSection> {
    assertUuid(projectId, 'project id')
    if (!sectionKey.trim()) throw new TypeError('section key must not be blank')
    assertUtcIsoDateTime(now, 'prd timestamp')

    return this.database.transaction('rw', this.tables(), async () => {
      const section = await this.getByKey(projectId, sectionKey)
      if (!section) throw new Error(`PRD section ${sectionKey} not found`)
      if (section.locked) throw new Error(`Cannot replace locked section: ${section.title}`)
      const updated: PrdSection = {
        ...section, content, status: 'COMPLETED' as PrdSectionStatus,
        errorCode: null, updatedAt: now,
      }
      await this.database.prd_section.put(updated)
      await this.recordVersion(projectId, `确认重新生成：${section.title}`, now)
      return updated
    })
  }

  private nextId() {
    const id = this.createId()
    assertUuid(id, 'prd section id')
    return id
  }

  private tables() {
    return [
      this.database.project, this.database.prd_section,
      this.database.requirement_item, this.database.clarification_question,
      this.database.clarification_answer, this.database.requirement_conflict,
      this.database.requirement_version, this.database.requirement_change,
    ]
  }

  private async recordVersion(projectId: string, summary: string, now: string) {
    const project = await this.database.project.get(projectId)
    if (!project) throw new Error(`Project ${projectId} not found`)
    const [requirements, questions, answers, conflicts] = await Promise.all([
      this.database.requirement_item.where('projectId').equals(projectId).toArray(),
      this.database.clarification_question.where('projectId').equals(projectId).toArray(),
      this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
      this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
    ])
    const updatedProject = { ...project, stage: 'PRD' as const, updatedAt: now }
    await this.database.project.put(updatedProject)
    const snapshot: RequirementStateSnapshot = {
      project: structuredClone(updatedProject),
      requirements: structuredClone(requirements),
      questions: structuredClone(questions),
      answers: structuredClone(answers),
      conflicts: structuredClone(conflicts),
    }
    const versionId = this.nextId()
    const changeId = this.nextId()
    await this.database.requirement_version.add({
      id: versionId, projectId, changeType: 'UPDATE', summary, snapshot, createdAt: now,
    })
    await this.database.requirement_change.add({
      id: changeId, projectId, versionId, requirementId: null,
      changeType: 'UPDATE', field: 'prd_sections', oldValue: null, newValue: summary, createdAt: now,
    })
  }
}

function sectionTitle(key: PrdSectionKey): string {
  const titles: Record<PrdSectionKey, string> = {
    'coding-agent-guide': 'Coding Agent 使用说明',
    'product-context': '产品背景、目标与非目标',
    'roles-permissions': '用户角色与权限',
    'features-priorities': '功能模块及优先级',
    'user-stories': '用户故事与功能摘要',
    'flows-state-machine': '核心业务流程与状态机',
    'rules-exceptions': '业务规则和异常场景',
    'architecture': '技术决策摘要与工程约束',
    'data-model': '数据实体、字段、关系和状态',
    'pages': '页面清单与页面行为',
    'apis': '接口契约、请求响应示例和错误码',
    'non-functional': '安全、性能和其他非功能需求',
    'acceptance': '普通验收规则和 Given/When/Then 场景',
    'implementation-phases': '分阶段实施计划',
    'test-strategy': '测试策略与关键测试用例',
    'prohibitions': '明确禁止事项',
    'open-items': 'AI 假设、待确认事项和已知限制',
  }
  return titles[key] ?? key
}

export const prdRepository = new PrdRepository()
