<script setup lang="ts">
import { computed } from 'vue'

import type { ProjectListFilter } from '@/db/repositories/projectRepository'

const props = defineProps<{ view: ProjectListFilter }>()
const emit = defineEmits<{ create: [] }>()

const content = computed(() => {
  if (props.view === 'ARCHIVED') {
    return {
      kicker: '保持工作区清爽',
      title: '还没有归档项目',
      description: '归档后的项目会保留全部本地数据，并集中显示在这里。',
    }
  }
  if (props.view === 'DELETED') {
    return {
      kicker: '可恢复的删除',
      title: '回收站为空',
      description: '移入回收站的项目会显示在这里，永久删除前仍可恢复。',
    }
  }
  return {
    kicker: '从一句想法开始',
    title: '还没有项目',
    description: '创建第一个需求项目，Prompt2PRD 会帮助你发现信息缺口，并逐步整理成结构化 PRD。',
  }
})
</script>

<template>
  <section class="empty-state" :aria-labelledby="`empty-state-title-${view}`">
    <div class="empty-state__icon" aria-hidden="true">
      <svg viewBox="0 0 48 48">
        <path d="M10 13.5A3.5 3.5 0 0 1 13.5 10h21a3.5 3.5 0 0 1 3.5 3.5v21a3.5 3.5 0 0 1-3.5 3.5h-21a3.5 3.5 0 0 1-3.5-3.5z" />
        <path d="M17 18h14M17 24h10M17 30h7" />
        <path class="empty-state__spark" d="m34 26 1.2 2.8L38 30l-2.8 1.2L34 34l-1.2-2.8L30 30l2.8-1.2z" />
      </svg>
    </div>
    <div class="empty-state__copy">
      <span>{{ content.kicker }}</span>
      <h3 :id="`empty-state-title-${view}`">{{ content.title }}</h3>
      <p>{{ content.description }}</p>
    </div>
    <button v-if="view === 'ACTIVE'" class="button-primary" type="button" @click="emit('create')">
      创建第一个项目
    </button>
  </section>
</template>

<style scoped>
.empty-state {
  display: flex;
  min-height: 410px;
  padding: 50px 32px 30px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  align-items: center;
  flex-direction: column;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.empty-state__icon {
  display: grid;
  width: 82px;
  height: 82px;
  border: 1px solid #cfe1e3;
  border-radius: 22px;
  place-items: center;
  background: var(--color-accent-soft);
}

.empty-state__icon svg {
  width: 45px;
  height: 45px;
  fill: none;
  stroke: var(--color-accent);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.empty-state__icon .empty-state__spark {
  fill: var(--color-primary);
  stroke: var(--color-text-primary);
  stroke-width: 1.2;
}

.empty-state__copy {
  max-width: 480px;
  margin: 22px 0 20px;
  text-align: center;
}

.empty-state__copy span {
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.06em;
}

.empty-state__copy h3 {
  margin: 7px 0 9px;
  font-size: 22px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.empty-state__copy p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.75;
}
</style>
