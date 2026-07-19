<script setup lang="ts">
export type StatusFilterValue = 'ALL' | 'CONFIRMED' | 'PENDING' | 'UNANALYZED' | 'CONFLICTED'

defineProps<{ activeFilter: StatusFilterValue; counts: Record<StatusFilterValue, number> }>()
defineEmits<{ 'update:activeFilter': [value: StatusFilterValue] }>()

const FILTERS: { value: StatusFilterValue; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'PENDING', label: '待确认' },
  { value: 'UNANALYZED', label: '待分析' },
  { value: 'CONFLICTED', label: '冲突' },
]
</script>
<template>
  <nav class="status-filter" aria-label="需求状态筛选">
    <template v-for="filter in FILTERS" :key="filter.value">
      <button
        v-if="filter.value === 'ALL' || counts[filter.value] > 0"
        type="button"
        class="status-filter__pill"
        :class="{ 'status-filter__pill--active': activeFilter === filter.value }"
        @click="$emit('update:activeFilter', filter.value)"
      >
        {{ filter.label }}
        <span class="status-filter__count">{{ counts[filter.value] }}</span>
      </button>
    </template>
  </nav>
</template>
<style scoped>
.status-filter {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  width: fit-content;
}
.status-filter__pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  transition: background 150ms ease, color 150ms ease;
}
.status-filter__pill:hover {
  background: var(--color-accent-soft);
  color: var(--color-text-primary);
}
.status-filter__pill--active {
  color: #fff;
  background: var(--color-accent);
  border-color: var(--color-accent);
}
.status-filter__count {
  font-size: 10px;
  font-weight: 600;
  min-width: 16px;
  text-align: center;
  line-height: 16px;
  border-radius: 999px;
  padding: 0 4px;
  background: rgba(255,255,255,0.25);
}
.status-filter__pill:not(.status-filter__pill--active) .status-filter__count {
  background: var(--color-border);
  color: var(--color-text-secondary);
}
</style>
