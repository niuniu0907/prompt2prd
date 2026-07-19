<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { RequirementItem, RequirementConflict, RequirementVersion } from './types'
import type { ManualRequirementEdit } from '@/db/repositories/requirementRepository'
import { structuredRequirementFields, canGenerateFlowchart } from './requirementDisplay'
import VersionHistory from '@/features/history/VersionHistory.vue'

const props = defineProps<{
  requirement: RequirementItem | null
  visible: boolean
  busy: boolean
  versions: RequirementVersion[]
  relatedConflicts: RequirementConflict[]
}>()

const emit = defineEmits<{
  close: []
  confirm: [requirementId: string]
  reject: [requirementId: string]
  lock: [requirementId: string, locked: boolean]
  saveEdit: [edit: ManualRequirementEdit]
  generateAcceptance: [requirement: RequirementItem]
  resolveConflict: [conflictId: string, resolution: string]
}>()

const router = useRouter()

const editing = ref(false)
const editTitle = ref('')
const editContent = ref('')

watch(() => props.requirement, (req) => {
  if (req) {
    editTitle.value = req.title
    editContent.value = req.content
    editing.value = false
  }
})

function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.visible) {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onEscape))
onBeforeUnmount(() => window.removeEventListener('keydown', onEscape))

function handleSave() {
  if (props.requirement && !props.requirement.locked) {
    if (editTitle.value !== props.requirement.title || editContent.value !== props.requirement.content) {
      emit('saveEdit', { title: editTitle.value, content: editContent.value, affectedArtifacts: ['FLOWCHART', 'PRD'] })
    }
  }
  editing.value = false
}

function handleGenerateFlowchart(req: RequirementItem) {
  void router.push({
    name: 'project-flowchart',
    params: { projectId: req.projectId },
    query: { requirementId: req.id },
  })
}

const typeLabels: Record<string, string> = {
  PRODUCT_GOAL: '产品目标', ROLE: '用户角色', FEATURE: '功能需求', USER_STORY: '用户故事',
  BUSINESS_RULE: '业务规则', EXCEPTION_SCENARIO: '异常场景', TECHNICAL_CONSTRAINT: '技术约束',
  DATA_MODEL: '数据模型', ACCEPTANCE_CRITERION: '验收标准', PAGE: '页面需求', API: '接口需求',
  IMPLEMENTATION_PHASE: '实施阶段', CODING_AGENT_CONSTRAINT: 'AI 编程约束',
  NON_FUNCTIONAL_REQUIREMENT: '非功能需求', ASSUMPTION: 'AI 假设', RISK_OPEN_ITEM: '风险与待确认项',
  MISSING_INFORMATION: '待补充信息',
}

const statusLabels: Record<string, string> = {
  UNANALYZED: '未分析', INFERRED: '待确认', PENDING: '待确认', CONFIRMED: '已确认',
  SKIPPED: '已跳过', NOT_APPLICABLE: '不适用', CONFLICTED: '存在冲突',
}

const sourceLabels: Record<string, string> = {
  INITIAL_INPUT: '用户输入', UPLOADED_FILE: '导入文档', USER_ANSWER: '用户回答',
  AI_INFERENCE: 'AI 推断', AI_RECOMMENDATION: 'AI 建议', USER_EDIT: '用户编辑', VERSION_RESTORE: '版本恢复',
}
</script>
<template>
  <Teleport to="body">
    <div
      class="drawer-overlay"
      :class="{ 'drawer-overlay--open': visible }"
      @click.self="$emit('close')"
    >
      <aside
        class="drawer"
        :class="{ 'drawer--open': visible }"
        role="dialog"
        aria-modal="true"
        :aria-label="requirement?.title ?? '需求详情'"
      >
        <template v-if="requirement">
          <header class="drawer__header">
            <div>
              <span class="drawer__type">{{ typeLabels[requirement.type] ?? requirement.type }}</span>
              <h2 class="drawer__title">{{ requirement.title }}</h2>
            </div>
            <button type="button" class="drawer__close" aria-label="关闭" @click="$emit('close')">
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
              </svg>
            </button>
          </header>

          <div class="drawer__body">
            <!-- Meta bar -->
            <div class="drawer__meta">
              <span class="drawer__status" :class="{
                'drawer__status--confirmed': requirement.status === 'CONFIRMED',
                'drawer__status--pending': requirement.status === 'INFERRED' || requirement.status === 'PENDING',
                'drawer__status--conflicted': requirement.status === 'CONFLICTED',
              }">{{ statusLabels[requirement.status] ?? requirement.status }}</span>
              <span class="drawer__source">{{ sourceLabels[requirement.sourceType] }}</span>
              <span v-if="requirement.locked" class="drawer__locked">🔒 已锁定</span>
            </div>

            <!-- Full content / edit form -->
            <div class="drawer__section">
              <h3 class="drawer__section-title">内容</h3>
              <template v-if="editing">
                <input
                  v-model="editTitle"
                  class="drawer__input"
                  :disabled="busy"
                  data-testid="drawer-edit-title"
                />
                <textarea
                  v-model="editContent"
                  class="drawer__textarea"
                  :disabled="busy"
                  data-testid="drawer-edit-content"
                  rows="6"
                />
                <div class="drawer__edit-actions">
                  <button type="button" class="btn-text" @click="editing = false">取消</button>
                  <button type="button" class="btn-primary" :disabled="busy" @click="handleSave">保存</button>
                </div>
              </template>
              <template v-else>
                <p v-if="requirement.locked" class="drawer__lock-notice">需求已锁定，请先解锁后再编辑。</p>
                <p class="drawer__content">{{ requirement.content }}</p>
                <dl v-if="structuredRequirementFields(requirement).length" class="drawer__fields">
                  <template v-for="field in structuredRequirementFields(requirement)" :key="field.label">
                    <dt>{{ field.label }}</dt>
                    <dd>{{ field.value }}</dd>
                  </template>
                </dl>
              </template>
            </div>

            <!-- Conflicts section -->
            <div v-if="relatedConflicts.length" class="drawer__section">
              <h3 class="drawer__section-title">关联冲突</h3>
              <div v-for="conflict in relatedConflicts" :key="conflict.id" class="drawer__conflict">
                <div class="drawer__conflict-header">
                  <strong>{{ conflict.core ? '核心冲突' : '普通冲突' }}</strong>
                  <span>{{ conflict.impact }}</span>
                </div>
                <div class="drawer__conflict-sides">
                  <p>{{ conflict.leftContent }}</p>
                  <b>或</b>
                  <p>{{ conflict.rightContent }}</p>
                </div>
                <button
                  v-if="conflict.status === 'OPEN'"
                  type="button"
                  class="btn-text"
                  data-testid="drawer-resolve-conflict"
                  @click="$emit('resolveConflict', conflict.id, '保留左侧内容')"
                >
                  保留左侧内容并解决
                </button>
              </div>
            </div>

            <!-- Version history -->
            <div v-if="versions.length" class="drawer__section">
              <h3 class="drawer__section-title">版本历史</h3>
              <VersionHistory :versions="versions" :busy="busy" @restore="$emit('close')" />
            </div>
          </div>

          <!-- Actions footer -->
          <footer class="drawer__footer">
            <button
              v-if="requirement.status !== 'CONFIRMED' && !requirement.locked"
              type="button"
              class="btn-secondary"
              data-testid="drawer-confirm"
              :disabled="busy"
              @click="$emit('confirm', requirement.id)"
            >确认</button>
            <button
              v-if="requirement.status !== 'SKIPPED' && !requirement.locked"
              type="button"
              class="btn-text"
              data-testid="drawer-reject"
              :disabled="busy"
              @click="$emit('reject', requirement.id)"
            >拒绝</button>
            <button
              type="button"
              class="btn-text"
              data-testid="drawer-lock"
              :disabled="busy || (!requirement.locked && requirement.status !== 'CONFIRMED')"
              @click="$emit('lock', requirement.id, !requirement.locked)"
            >{{ requirement.locked ? '解锁' : '锁定' }}</button>
            <button
              v-if="!requirement.locked"
              type="button"
              class="btn-text"
              :disabled="busy"
              @click="editing = true"
            >编辑</button>
            <button
              type="button"
              class="btn-text"
              data-testid="drawer-generate-acceptance"
              :disabled="busy"
              @click="$emit('generateAcceptance', requirement)"
            >生成验收标准</button>
            <button
              v-if="canGenerateFlowchart(requirement)"
              type="button"
              class="btn-text"
              data-testid="drawer-generate-flowchart"
              :disabled="busy"
              @click="handleGenerateFlowchart(requirement)"
            >生成流程图</button>
          </footer>
        </template>
      </aside>
    </div>
  </Teleport>
</template>
<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(0,0,0,0.3);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion-base) var(--ease-standard);
}
.drawer-overlay--open {
  opacity: 1;
  pointer-events: auto;
}
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  width: 460px;
  max-width: 90vw;
  background: var(--color-surface);
  box-shadow: -4px 0 24px rgba(0,0,0,0.12);
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform var(--motion-base) var(--ease-standard);
}
.drawer--open {
  transform: translateX(0);
}
.drawer__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.drawer__type {
  font-size: 10px;
  font-weight: 600;
  color: var(--color-accent);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.drawer__title {
  margin: 4px 0 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.35;
  word-break: break-word;
}
.drawer__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
}
.drawer__close:hover {
  background: var(--color-surface-muted);
  color: var(--color-text-primary);
}
.drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: grid;
  gap: 18px;
  align-content: start;
}
.drawer__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.drawer__status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}
.drawer__status--confirmed {
  color: #246b58;
  background: #eefaf5;
}
.drawer__status--pending {
  color: #765313;
  background: #fff9e8;
}
.drawer__status--conflicted {
  color: #873f3f;
  background: #fff8f8;
}
.drawer__source {
  font-size: 11px;
  color: var(--color-text-muted);
}
.drawer__locked {
  font-size: 11px;
  color: var(--color-text-muted);
}
.drawer__section {
  display: grid;
  gap: 10px;
}
.drawer__section-title {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.drawer__content {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-primary);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.drawer__lock-notice {
  margin: 0;
  font-size: 12px;
  color: #873f3f;
}
.drawer__fields {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 7px 12px;
  margin: 0;
}
.drawer__fields dt {
  color: var(--color-text-secondary);
  font-size: 11px;
}
.drawer__fields dd {
  min-width: 0;
  margin: 0;
  color: var(--color-text-primary);
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.drawer__input {
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--color-text-primary);
}
.drawer__textarea {
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-family: inherit;
  color: var(--color-text-primary);
  min-height: 140px;
  resize: vertical;
}
.drawer__edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.drawer__conflict {
  padding: 12px;
  border: 1px solid #e2bcbc;
  border-radius: var(--radius-sm);
  background: #fffafa;
  display: grid;
  gap: 8px;
}
.drawer__conflict-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
}
.drawer__conflict-header span {
  color: var(--color-text-secondary);
  font-size: 10px;
}
.drawer__conflict-sides {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
}
.drawer__conflict-sides p {
  margin: 0;
  padding: 8px;
  background: var(--color-surface);
  font-size: 11px;
  border-radius: 4px;
}
.drawer__conflict-sides b {
  color: var(--color-text-muted);
  font-size: 9px;
}
.drawer__footer {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}
.btn-primary {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-on-accent);
  background: var(--color-primary);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.btn-primary:hover {
  filter: brightness(0.95);
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-text {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--color-accent);
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 6px;
}
.btn-text:hover {
  background: var(--color-accent-soft);
}
.btn-text:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.btn-secondary {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-accent);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.btn-secondary:hover {
  background: var(--color-accent-soft);
  border-color: var(--color-accent);
}
.btn-secondary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
