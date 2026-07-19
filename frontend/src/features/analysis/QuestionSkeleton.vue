<script setup lang="ts">
defineProps<{ count?: number }>()
</script>

<template>
  <section class="question-skeleton" aria-label="正在加载下一轮问题">
    <header class="question-skeleton__header">
      <div class="question-skeleton__round-label shimmer" />
      <div class="question-skeleton__title shimmer" />
      <div class="question-skeleton__subtitle shimmer" />
    </header>
    <article
      v-for="i in (count ?? 8)"
      :key="i"
      class="question-skeleton__card"
    >
      <div class="question-skeleton__card-badge shimmer" />
      <div class="question-skeleton__card-title shimmer" />
      <div class="question-skeleton__card-text shimmer" />
      <div class="question-skeleton__card-options">
        <div v-for="j in 3" :key="j" class="question-skeleton__option shimmer" />
      </div>
    </article>
  </section>
</template>

<style scoped>
.question-skeleton {
  display: grid;
  gap: 14px;
  max-width: 820px;
  margin: 0 auto;
}

.question-skeleton__header {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.question-skeleton__round-label {
  width: 80px;
  height: 12px;
  border-radius: 4px;
}

.question-skeleton__title {
  width: 220px;
  height: 22px;
  border-radius: 4px;
}

.question-skeleton__subtitle {
  width: 360px;
  height: 14px;
  border-radius: 4px;
}

.question-skeleton__card {
  display: grid;
  gap: 12px;
  padding: 19px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.question-skeleton__card-badge {
  width: 50px;
  height: 10px;
  border-radius: 4px;
}

.question-skeleton__card-title {
  width: 70%;
  height: 16px;
  border-radius: 4px;
}

.question-skeleton__card-text {
  width: 90%;
  height: 12px;
  border-radius: 4px;
}

.question-skeleton__card-options {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.question-skeleton__option {
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
}

.shimmer {
  background: linear-gradient(
    90deg,
    var(--color-border) 25%,
    var(--color-surface) 50%,
    var(--color-border) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
