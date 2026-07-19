<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  sectionTitle: string
  visible: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const confirmed = ref(false)
</script>

<template>
  <div
    v-if="visible"
    class="dialog-overlay"
    data-testid="regen-dialog-overlay"
    @click.self="emit('cancel')"
  >
    <div class="dialog" role="alertdialog" data-testid="regen-dialog">
      <h3>重新生成章节</h3>
      <p>
        重新生成「<strong>{{ sectionTitle }}</strong>」将会：
      </p>
      <ul>
        <li>保存当前内容为历史版本（可恢复）</li>
        <li>使用当前已确认需求重新生成该章节</li>
        <li>可能覆盖您手动编辑的内容</li>
      </ul>
      <label class="confirm-check">
        <input v-model="confirmed" type="checkbox" data-testid="regen-confirm-check" />
        我确认要重新生成此章节
      </label>
      <div class="dialog-actions">
        <button
          type="button"
          class="cancel-btn"
          data-testid="regen-cancel"
          @click="emit('cancel')"
        >
          取消
        </button>
        <button
          type="button"
          class="confirm-btn"
          :disabled="!confirmed"
          data-testid="regen-confirm"
          @click="emit('confirm')"
        >
          确认重新生成
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  z-index: 100;
}
.dialog {
  width: 420px;
  max-width: 90vw;
  padding: 20px;
  border-radius: 11px;
  background: var(--color-surface);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18);
}
.dialog h3 {
  margin: 0 0 10px;
  font-size: 16px;
}
.dialog p {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.dialog ul {
  margin: 0 0 14px;
  padding-left: 18px;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.confirm-check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 14px;
  cursor: pointer;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.cancel-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  font-size: 11px;
  cursor: pointer;
}
.confirm-btn {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent);
  color: #262b25;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
