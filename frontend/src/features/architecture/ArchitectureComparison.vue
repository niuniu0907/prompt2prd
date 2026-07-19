<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { ArchitectureCandidate, ScoreDimension } from './types'
import { SCORE_DIMENSIONS } from './types'

const props = defineProps<{
  candidates: ArchitectureCandidate[]
  pendingFields?: string[]
  confirmedId?: string | null
  busy?: boolean
}>()
const emit = defineEmits<{ confirm: [candidate: ArchitectureCandidate, manual: boolean] }>()

const scoreLabels: Record<ScoreDimension, string> = {
  LEARNING_COST: '学习成本', DEVELOPMENT_SPEED: '开发速度', DEPLOYMENT_SIMPLICITY: '部署简单度',
  RUNNING_COST: '运行成本', MAINTAINABILITY: '可维护性', SCALABILITY: '扩展性', AI_SUPPORT: 'AI 支持',
}
const stackLabels: Record<string, string> = {
  frontend: '前端', backend: '后端', storage: '数据存储', authentication: '鉴权',
  fileStorage: '文件存储', ai: 'AI 接入', deployment: '部署', testing: '测试',
}
const editingId = ref<string | null>(null)
const manualName = ref('')
const manualStack = reactive<Record<string, string>>({})
const recommended = computed(() => props.candidates.find(candidate => candidate.recommended))
const detailOpen = ref(false)

watch(() => props.candidates, candidates => {
  if (editingId.value && !candidates.some(candidate => candidate.id === editingId.value)) editingId.value = null
})

function beginManual(candidate: ArchitectureCandidate) {
  editingId.value = candidate.id
  manualName.value = `${candidate.name}（手动调整）`
  for (const key of Object.keys(manualStack)) delete manualStack[key]
  Object.assign(manualStack, candidate.stack)
}

function confirmManual(candidate: ArchitectureCandidate) {
  const name = manualName.value.trim()
  if (!name) return
  emit('confirm', {
    ...candidate,
    id: crypto.randomUUID(),
    name,
    stack: { ...manualStack },
    recommended: false,
  }, true)
  editingId.value = null
}
</script>

<template>
  <section class="comparison" aria-label="架构候选对比">
    <header><div><span>步骤 2</span><h1>确认推荐架构</h1></div><p v-if="recommended">推荐方案已按技术基础、周期和部署复杂度综合排序。</p></header>
    <div v-if="pendingFields?.length" class="pending" role="status">仍待确认：{{ pendingFields.join('、') }}。当前结果保持草稿标记。</div>

    <article v-if="recommended" class="recommended" data-testid="recommended-architecture">
      <div>
        <span class="badge">推荐方案</span>
        <h2>{{ recommended.name }}</h2>
        <p>推荐原因：适合当前技术基础，开发周期短，部署和个人维护成本低。</p>
      </div>
      <dl class="recommended-stack">
        <template v-for="(value, key) in recommended.stack" :key="key">
          <dt>{{ stackLabels[String(key)] ?? key }}</dt>
          <dd>{{ value }}</dd>
        </template>
      </dl>
      <div class="recommended-actions">
        <button type="button" @click="detailOpen = !detailOpen">
          {{ detailOpen ? '收起详细比较' : '查看详细比较' }}
        </button>
        <button
          type="button"
          class="button-primary"
          :disabled="busy || confirmedId === recommended.id"
          @click="emit('confirm', recommended, false)"
        >
          {{ confirmedId === recommended.id ? '已采用此方案' : '采用此方案' }}
        </button>
      </div>
    </article>

    <div v-if="detailOpen" class="score-note">
      评分采用学习成本、开发速度、部署简单度、运行成本、可维护性、扩展性和 AI 支持七个维度，每项 1 到 5 分，作为比较依据而不是最终决定。
    </div>

    <div v-if="detailOpen" class="cards">
      <article v-for="candidate in candidates" :key="candidate.id" class="candidate" :data-testid="`candidate-${candidate.id}`">
        <div class="candidate__heading"><div><span v-if="candidate.recommended" class="badge">推荐</span><span v-if="confirmedId === candidate.id" class="badge badge--confirmed">主架构</span><h2>{{ candidate.name }}</h2></div><strong>{{ candidate.totalScore }}/35</strong></div>
        <dl class="stack"><template v-for="(value, key) in candidate.stack" :key="key"><dt>{{ stackLabels[String(key)] ?? key }}</dt><dd>{{ value }}</dd></template></dl>
        <div class="scores"><div v-for="dimension in SCORE_DIMENSIONS" :key="dimension"><span>{{ scoreLabels[dimension] }}</span><b>{{ candidate.scores[dimension] }}/5</b></div></div>
        <div class="details"><section><h3>职责</h3><ul><li v-for="item in candidate.responsibilities" :key="item">{{ item }}</li></ul></section><section><h3>优点</h3><ul><li v-for="item in candidate.advantages" :key="item">{{ item }}</li></ul></section><section><h3>缺点与限制</h3><ul><li v-for="item in [...candidate.disadvantages, ...candidate.limitations]" :key="item">{{ item }}</li></ul></section><section><h3>未选择原因</h3><ul><li v-for="item in candidate.unselectedReasons" :key="item">{{ item }}</li></ul></section></div>
        <div v-if="editingId === candidate.id" class="manual" data-testid="manual-editor"><label>方案名称<input v-model="manualName" name="manualName"></label><label v-for="(_, key) in manualStack" :key="key">{{ stackLabels[String(key)] ?? key }}<input v-model="manualStack[String(key)]" :name="`manual-${String(key)}`"></label><div><button type="button" @click="editingId = null">取消</button><button type="button" class="button-primary" :disabled="busy" @click="confirmManual(candidate)">确认手动方案</button></div></div>
        <div v-else class="actions"><button type="button" :disabled="busy || confirmedId === candidate.id" @click="emit('confirm', candidate, false)">{{ confirmedId === candidate.id ? '已确认' : candidate.recommended ? '接受推荐' : '选择此方案' }}</button><button type="button" :disabled="busy" @click="beginManual(candidate)">手动修改</button></div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.comparison{display:grid;gap:15px}.comparison>header{display:flex;align-items:end;justify-content:space-between}.comparison header span{font-size:11px;font-weight:750;color:var(--color-accent)}h1{margin:4px 0 0;font-size:22px}.comparison header p{margin:0;color:var(--color-text-secondary);font-size:12px}.pending{padding:10px 12px;border:1px solid #ead9a6;border-radius:8px;background:#fff9e8;color:#765313;font-size:11px}.recommended{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(320px,.9fr) auto;gap:18px;align-items:center;padding:22px;border:1px solid var(--color-border);border-left:4px solid var(--color-primary);border-radius:10px;background:var(--color-surface);box-shadow:var(--shadow-card)}.recommended h2{margin:8px 0 7px;font-size:20px}.recommended p{margin:0;color:var(--color-text-secondary);font-size:13px;line-height:1.6}.recommended-stack{display:grid;grid-template-columns:84px minmax(0,1fr);gap:7px 12px;margin:0}.recommended-stack dt{color:var(--color-text-secondary);font-size:11px}.recommended-stack dd{margin:0;color:var(--color-text-primary);font-size:12px;overflow-wrap:anywhere}.recommended-actions{display:grid;gap:8px;justify-items:stretch}.recommended-actions button{min-height:38px;padding:0 14px;border:1px solid var(--color-border);border-radius:8px;background:var(--color-surface);font-size:12px;cursor:pointer}.recommended-actions .button-primary{border-color:var(--color-primary);background:var(--color-primary);color:var(--color-text-primary)}.score-note{padding:11px 13px;border:1px solid var(--color-border);border-radius:8px;color:var(--color-text-secondary);background:var(--color-surface);font-size:12px;line-height:1.6}.cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.candidate{display:grid;align-content:start;gap:12px;padding:15px;border:1px solid var(--color-border);border-radius:11px;background:var(--color-surface);box-shadow:var(--shadow-card)}.candidate__heading{display:flex;justify-content:space-between;gap:8px}.candidate h2{margin:4px 0 0;font-size:15px}.candidate__heading strong{font-size:12px;color:var(--color-accent)}.badge{display:inline-block;margin-right:5px;padding:2px 6px;border-radius:99px;background:var(--color-primary);color:var(--color-text-primary);font-size:9px}.badge--confirmed{background:var(--color-accent);color:var(--color-on-accent)}.stack{display:grid;grid-template-columns:56px 1fr;gap:5px;margin:0;font-size:10px}.stack dt{color:var(--color-text-secondary)}.stack dd{margin:0}.scores{display:grid;gap:4px;padding-top:8px;border-top:1px solid var(--color-border)}.scores div{display:flex;justify-content:space-between;font-size:10px}.details{display:grid;gap:8px}.details section h3{margin:0 0 4px;font-size:10px}.details ul{margin:0;padding-left:16px;color:var(--color-text-secondary);font-size:9px;line-height:1.5}.actions{display:flex;gap:7px;margin-top:auto}.actions button,.manual button{padding:7px 9px;border:1px solid var(--color-border);border-radius:6px;background:var(--color-surface);font-size:10px}.actions button:first-child,.manual .button-primary{border-color:var(--color-primary);background:var(--color-primary);color:var(--color-text-primary)}.manual{display:grid;gap:7px;padding-top:8px;border-top:1px solid var(--color-border)}.manual label{display:grid;gap:3px;color:var(--color-text-secondary);font-size:9px}.manual input{min-width:0;padding:6px;border:1px solid var(--color-border);border-radius:5px}.manual>div{display:flex;gap:7px}@media(max-width:1180px){.recommended{grid-template-columns:1fr}.recommended-actions{justify-items:start}.cards{grid-template-columns:1fr}}
</style>
