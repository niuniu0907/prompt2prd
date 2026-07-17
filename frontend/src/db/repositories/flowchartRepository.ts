import type { AppDatabase } from '@/db/appDatabase'
import { appDatabase } from '@/db/appDatabase'
import type { FlowchartDraft, FlowchartRecord } from '@/features/flowchart/types'
import { validateMermaid, type MermaidValidationResult } from '@/features/flowchart/mermaidValidator'
import { assertUuid, assertUtcIsoDateTime } from '@/features/projects/types'
import type { RequirementStateSnapshot } from '@/features/requirements/types'

export interface FlowchartSaveFailure {
  draft: FlowchartDraft
  error: string
}

export interface FlowchartSaveResult {
  saved: FlowchartRecord[]
  failures: FlowchartSaveFailure[]
}

export class FlowchartRepository {
  constructor(
    private readonly database: AppDatabase = appDatabase,
    private readonly validator: (source: string) => Promise<MermaidValidationResult> = validateMermaid,
    private readonly createId: () => string = () => crypto.randomUUID(),
  ) {}

  async listByProject(projectId: string): Promise<FlowchartRecord[]> {
    assertUuid(projectId, 'project id')
    const records = await this.database.flowchart.where('projectId').equals(projectId).toArray()
    return records.sort((left, right) => left.type === right.type
      ? left.title.localeCompare(right.title)
      : left.type === 'MAIN' ? -1 : 1)
  }

  async getByKey(projectId: string, key: string): Promise<FlowchartRecord | undefined> {
    assertUuid(projectId, 'project id')
    if (!key.trim()) throw new TypeError('flowchart key must not be blank')
    return this.database.flowchart.where('[projectId+key]').equals([projectId, key.trim()]).first()
  }

  async saveGenerated(
    projectId: string,
    drafts: FlowchartDraft[],
    now = new Date().toISOString(),
  ): Promise<FlowchartSaveResult> {
    assertUuid(projectId, 'project id'); assertUtcIsoDateTime(now, 'flowchart timestamp')
    const saved: FlowchartRecord[] = []
    const failures: FlowchartSaveFailure[] = []
    const validated: FlowchartDraft[] = []
    for (const draft of drafts) {
      try {
        this.validateDraft(draft)
        const validation = await this.validator(draft.mermaid)
        if (!validation.valid) {
          failures.push({ draft, error: validation.message ?? 'Mermaid 语法无效。' })
          continue
        }
        validated.push(draft)
      } catch (error) {
        failures.push({ draft, error: readable(error) })
      }
    }
    if (validated.length > 0) {
      await this.database.transaction('rw', this.tables(), async () => {
        for (const draft of validated) {
          if (await this.getByKey(projectId, draft.key)) {
            failures.push({ draft, error: '已有同名流程图，替换前需要用户确认。' })
            continue
          }
          const record = this.toRecord(projectId, draft, this.nextId(), now, now)
          await this.database.flowchart.add(record)
          saved.push(record)
        }
        if (saved.length > 0) await this.recordVersion(projectId, '保存流程图', null, saved, now)
      })
    }
    return { saved, failures }
  }

  async replaceAfterConfirmation(
    projectId: string,
    draft: FlowchartDraft,
    now = new Date().toISOString(),
  ): Promise<FlowchartRecord> {
    assertUuid(projectId, 'project id'); assertUtcIsoDateTime(now, 'replacement timestamp')
    this.validateDraft(draft)
    const validation = await this.validator(draft.mermaid)
    if (!validation.valid) throw new TypeError(validation.message ?? 'Mermaid 语法无效。')
    return this.database.transaction('rw', this.tables(), async () => {
      const current = await this.getByKey(projectId, draft.key)
      const record = this.toRecord(projectId, draft, current?.id ?? this.nextId(),
        current?.createdAt ?? now, now)
      await this.database.flowchart.put(record)
      await this.recordVersion(projectId, `替换流程图：${record.title}`, current ?? null, record, now)
      return record
    })
  }

  private validateDraft(draft: FlowchartDraft) {
    if (!draft.key.trim() || !draft.title.trim()) throw new TypeError('flowchart key and title must not be blank')
    for (const id of draft.sourceRequirementIds) assertUuid(id, 'flowchart source requirement id')
  }

  private toRecord(projectId: string, draft: FlowchartDraft, id: string,
    createdAt: string, updatedAt: string): FlowchartRecord {
    return { id, projectId, key: draft.key.trim(), type: draft.type, title: draft.title.trim(),
      mermaid: draft.mermaid.trim(), status: 'VALID', sourceRequirementIds: [...draft.sourceRequirementIds],
      createdAt, updatedAt }
  }

  private nextId() { const id = this.createId(); assertUuid(id, 'flowchart id'); return id }

  private tables() {
    return [this.database.project, this.database.requirement_item, this.database.clarification_question,
      this.database.clarification_answer, this.database.requirement_conflict, this.database.flowchart,
      this.database.requirement_version, this.database.requirement_change]
  }

  private async recordVersion(projectId: string, summary: string, oldValue: unknown,
    newValue: unknown, now: string) {
    const project = await this.database.project.get(projectId)
    if (!project) throw new Error(`Project ${projectId} not found`)
    const [requirements, questions, answers, conflicts, flowcharts] = await Promise.all([
      this.database.requirement_item.where('projectId').equals(projectId).toArray(),
      this.database.clarification_question.where('projectId').equals(projectId).toArray(),
      this.database.clarification_answer.where('projectId').equals(projectId).toArray(),
      this.database.requirement_conflict.where('projectId').equals(projectId).toArray(),
      this.database.flowchart.where('projectId').equals(projectId).toArray(),
    ])
    const updatedProject = { ...project, stage: 'FLOWCHART' as const, updatedAt: now }
    await this.database.project.put(updatedProject)
    const snapshot: RequirementStateSnapshot = { project: structuredClone(updatedProject),
      requirements: structuredClone(requirements), questions: structuredClone(questions),
      answers: structuredClone(answers), conflicts: structuredClone(conflicts),
      flowcharts: structuredClone(flowcharts) }
    const versionId = this.nextId(); const changeId = this.nextId()
    await this.database.requirement_version.add({ id: versionId, projectId, changeType: 'UPDATE', summary, snapshot, createdAt: now })
    await this.database.requirement_change.add({ id: changeId, projectId, versionId, requirementId: null,
      changeType: 'UPDATE', field: 'flowcharts', oldValue, newValue, createdAt: now })
  }
}

function readable(error: unknown) { return error instanceof Error ? error.message : '流程图保存失败。' }

export const flowchartRepository = new FlowchartRepository()
