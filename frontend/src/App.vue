<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()
</script>

<template>
  <div class="toast-container" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="toast"
        :class="`toast--${t.type}`"
      >{{ t.message }}</div>
    </TransitionGroup>
  </div>
  <RouterView v-slot="{ Component, route }">
    <Transition :name="(route.meta.transition as string) || 'page'" mode="out-in">
      <component :is="Component" :key="route.path" />
    </Transition>
  </RouterView>
</template>

<style>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 200;
  display: grid;
  gap: 8px;
  pointer-events: none;
}

.toast {
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--color-text-primary);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  pointer-events: auto;
  min-width: 180px;
}

.toast--success { border-left: 3px solid #246b58; }
.toast--error   { border-left: 3px solid #873f3f; }
.toast--info    { border-left: 3px solid var(--color-accent); }

.toast-enter-active { transition: all var(--motion-base) var(--ease-standard); }
.toast-leave-active { transition: all var(--motion-base) var(--ease-standard); }
.toast-enter-from   { opacity: 0; transform: translateX(16px); }
.toast-leave-to     { opacity: 0; transform: translateX(16px); }

/* Page-level route transitions */
.page-enter-active {
  transition: opacity var(--motion-base) var(--ease-standard),
              transform var(--motion-base) var(--ease-standard);
}
.page-leave-active {
  transition: opacity var(--motion-base) var(--ease-standard);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-leave-to {
  opacity: 0;
}
</style>
