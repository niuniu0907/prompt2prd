<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'

import {
  quotaClient,
  type QuotaClient,
  type QuotaSnapshot,
} from '@/api/quotaApi'
import { useModelConfigStore } from '@/stores/modelConfigStore'

const props = defineProps<{ client?: QuotaClient }>()
const client = props.client ?? quotaClient
const store = useModelConfigStore()
const { selectedKeySource, systemKeyAvailable } = storeToRefs(store)

const quota = ref<QuotaSnapshot | null>(null)
const loading = ref(true)
const loadError = ref('')

const analysisExhausted = computed(() =>
  systemKeyAvailable.value && (quota.value?.analysisRemaining ?? 0) <= 0)
const prdExhausted = computed(() =>
  systemKeyAvailable.value && (quota.value?.fullPrdRemaining ?? 0) <= 0)
const globalExhausted = computed(() =>
  systemKeyAvailable.value && (quota.value?.globalCallsRemaining ?? 0) <= 0)
const anyExhausted = computed(() =>
  analysisExhausted.value || prdExhausted.value || globalExhausted.value)

const analysisUrgent = computed(() =>
  systemKeyAvailable.value && quota.value !== null && quota.value.analysisRemaining === 1)

onMounted(async () => {
  await refresh()
})

async function refresh() {
  loading.value = true
  loadError.value = ''
  try {
    quota.value = await client.getQuota()
    store.setSystemKeyAvailable(quota.value.systemKeyAvailable)
  } catch {
    loadError.value = '无法读取系统额度信息'
    quota.value = null
  } finally {
    loading.value = false
  }
}

function switchToUserKey() {
  store.selectKeySource('USER')
}
</script>

<template>
  <section class="quota-indicator" data-testid="quota-indicator">
    <header class="quota-indicator__header">
      <span>系统额度</span>
      <button
        v-if="!loading"
        class="quota-indicator__refresh"
        data-testid="quota-refresh"
        @click="refresh"
      >
        刷新
      </button>
    </header>

    <p v-if="loading" class="quota-indicator__loading" role="status">正在读取额度…</p>

    <p v-else-if="loadError" class="quota-indicator__error" role="alert">
      {{ loadError }}
    </p>

    <template v-else-if="!systemKeyAvailable">
      <p class="quota-indicator__disabled">
        系统 Key 未启用。使用你自己的 API Key 不受额度限制。
      </p>
    </template>

    <template v-else-if="quota">
      <dl class="quota-indicator__grid">
        <div class="quota-indicator__stat" data-testid="quota-analysis">
          <dt>分析次数</dt>
          <dd :class="{ 'quota-indicator__value--exhausted': analysisExhausted }">
            {{ quota.analysisRemaining }}
          </dd>
        </div>
        <div class="quota-indicator__stat" data-testid="quota-prd">
          <dt>PRD 生成</dt>
          <dd :class="{ 'quota-indicator__value--exhausted': prdExhausted }">
            {{ quota.fullPrdRemaining }}
          </dd>
        </div>
        <div class="quota-indicator__stat" data-testid="quota-global">
          <dt>全局调用</dt>
          <dd :class="{ 'quota-indicator__value--exhausted': globalExhausted }">
            {{ quota.globalCallsRemaining }}
          </dd>
        </div>
      </dl>

      <p v-if="analysisUrgent" class="quota-indicator__urgent" role="alert">
        分析次数仅剩 1 次，操作后系统 Key 将无法继续使用。
      </p>

      <div v-if="anyExhausted" class="quota-indicator__exhausted" role="alert">
        <p>
          <template v-if="analysisExhausted">今日分析次数已用尽。</template>
          <template v-if="prdExhausted">今日 PRD 生成次数已用尽。</template>
          <template v-if="globalExhausted">全局调用预算已耗尽。</template>
        </p>
        <p v-if="selectedKeySource === 'SYSTEM'" class="quota-indicator__guidance">
          系统 Key 额度为零，系统 Key 调用已被禁止。
          <button
            class="quota-indicator__switch-link"
            data-testid="quota-switch-to-user"
            @click="switchToUserKey"
          >
            切换到用户 Key
          </button>
          以继续使用，或等待次日额度重置。
        </p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.quota-indicator {
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.quota-indicator__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.quota-indicator__header span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.quota-indicator__refresh {
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  color: var(--color-accent);
  font-size: 12px;
  background: transparent;
  cursor: pointer;
}

.quota-indicator__loading,
.quota-indicator__error,
.quota-indicator__disabled {
  margin: 0;
  padding: 10px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.quota-indicator__error {
  color: #8b4141;
}

.quota-indicator__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.quota-indicator__stat {
  text-align: center;
  padding: 12px 8px;
  border-radius: 10px;
  background: var(--color-surface-muted);
}

.quota-indicator__stat dt {
  font-size: 11px;
  color: var(--color-text-secondary);
}

.quota-indicator__stat dd {
  margin: 6px 0 0;
  font-size: 22px;
  font-weight: 750;
  color: var(--color-accent);
}

.quota-indicator__value--exhausted {
  color: #c44141;
}

.quota-indicator__urgent {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-left: 3px solid #e0a040;
  color: #8b6914;
  font-size: 12px;
  background: #fffaf0;
  border-radius: 0 8px 8px 0;
}

.quota-indicator__exhausted {
  margin: 12px 0 0;
  padding: 12px 14px;
  border: 1px solid #e6c8c8;
  border-radius: 10px;
  background: #fff5f5;
}

.quota-indicator__exhausted p {
  margin: 0;
  color: #8b4141;
  font-size: 13px;
  line-height: 1.5;
}

.quota-indicator__guidance {
  margin-top: 8px !important;
  font-size: 12px !important;
}

.quota-indicator__switch-link {
  padding: 0;
  border: 0;
  color: var(--color-accent);
  font-size: inherit;
  font-weight: 680;
  background: none;
  text-decoration: underline;
  cursor: pointer;
}
</style>
