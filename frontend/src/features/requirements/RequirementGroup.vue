<script setup lang="ts">
defineProps<{ label: string; count: number; collapsed: boolean }>()
defineEmits<{ toggle: [] }>()
</script>
<template>
  <section class="requirement-group" :class="{ 'requirement-group--collapsed': collapsed }">
    <header
      class="requirement-group__header"
      role="button"
      :aria-expanded="!collapsed"
      @click="$emit('toggle')"
    >
      <svg
        class="requirement-group__chevron"
        :class="{ 'requirement-group__chevron--open': !collapsed }"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M9 18l6-6-6-6" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
      </svg>
      <h3 class="requirement-group__label">{{ label }}</h3>
      <span class="requirement-group__count">{{ count }}</span>
    </header>
    <div class="requirement-group__body">
      <div class="requirement-group__list">
        <slot />
      </div>
    </div>
  </section>
</template>
<style scoped>
.requirement-group {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  overflow: hidden;
}
.requirement-group__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--color-border);
}
.requirement-group--collapsed .requirement-group__header {
  border-bottom: 0;
}
.requirement-group__header:hover {
  background: var(--color-surface-muted);
}
.requirement-group__chevron {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--color-text-muted);
  transition: transform var(--motion-base) var(--ease-standard);
  transform: rotate(-90deg);
}
.requirement-group__chevron--open {
  transform: rotate(0deg);
}
.requirement-group__label {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  flex: 1;
}
.requirement-group__count {
  font-size: 11px;
  color: var(--color-text-muted);
  background: var(--color-surface-muted);
  border-radius: 999px;
  padding: 2px 8px;
  min-width: 20px;
  text-align: center;
}
.requirement-group__body {
  display: grid;
  grid-template-rows: 1fr;
  overflow: hidden;
  transition: grid-template-rows var(--motion-slow) var(--ease-standard);
}
.requirement-group--collapsed .requirement-group__body {
  grid-template-rows: 0fr;
}
.requirement-group__list {
  min-height: 0;
  display: grid;
  gap: 1px;
  background: var(--color-border);
  transition: opacity var(--motion-slow) var(--ease-standard);
}
.requirement-group--collapsed .requirement-group__list {
  opacity: 0;
}
</style>
