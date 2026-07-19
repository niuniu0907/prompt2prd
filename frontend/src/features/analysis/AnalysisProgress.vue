<script setup lang="ts">
defineProps<{
  progress: number
  message: string
  active: boolean
}>()
</script>

<template>
  <section class="progress-card" aria-live="polite">
    <div class="progress-card__heading">
      <div>
        <span>{{ active ? 'AI 正在梳理需求' : '当前分析状态' }}</span>
        <strong>{{ message }}</strong>
      </div>
      <b>{{ progress }}%</b>
    </div>
    <div
      class="progress-card__track"
      :class="{ 'progress-card__track--active': active && progress < 100 }"
      role="progressbar"
      :aria-valuenow="progress"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <i :style="{ width: `${progress}%` }" />
    </div>
  </section>
</template>

<style scoped>
.progress-card { padding: 18px 20px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface); box-shadow: var(--shadow-card); }
.progress-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.progress-card__heading div { display: grid; gap: 5px; }
.progress-card span { color: var(--color-accent); font-size: 10px; font-weight: 750; letter-spacing: .06em; }
.progress-card strong { font-size: 13px; }
.progress-card b { font-size: 22px; font-variant-numeric: tabular-nums; }
.progress-card__track { position: relative; height: 6px; margin-top: 15px; overflow: hidden; border-radius: 999px; background: var(--color-surface-muted); }
.progress-card__track i { display: block; height: 100%; border-radius: inherit; background: var(--color-accent); transition: width 180ms ease; }
.progress-card__track--active::after { position: absolute; inset: 0; width: 36%; border-radius: inherit; background: linear-gradient(90deg, transparent, rgba(255,255,255,.42), transparent); animation: progress-sweep 1.4s linear infinite; content: ""; }
@keyframes progress-sweep { from { transform: translateX(-120%); } to { transform: translateX(300%); } }
</style>
