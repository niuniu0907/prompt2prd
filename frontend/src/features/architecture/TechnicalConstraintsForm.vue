<script setup lang="ts">
import { reactive, ref } from 'vue'
import type {
  Budget,
  DataSensitivity,
  Deployment,
  MaintenanceCapacity,
  TargetPlatform,
  TeamSize,
  TechnicalConstraints,
  Timeline,
  UserScale,
} from './types'

const props = defineProps<{ projectId: string; busy?: boolean }>()
const emit = defineEmits<{ submit: [constraints: TechnicalConstraints] }>()

const technologies = ['Vue 3', 'TypeScript', 'Java', 'Spring Boot', 'Node.js', 'NestJS']
const selectedTechnologies = ref<string[]>([])
const customTechnology = ref('')
const targetPlatform = ref<TargetPlatform | ''>('')
const teamSize = ref<TeamSize | ''>('')
const userScale = ref<UserScale | ''>('')
const dataSensitivity = ref<DataSensitivity | ''>('')
const deployment = ref<Deployment | ''>('')
const budget = ref<Budget | ''>('')
const timeline = ref<Timeline | ''>('')
const maintenanceCapacity = ref<MaintenanceCapacity | ''>('')
const capabilitiesAnswered = ref(false)
const capabilities = reactive({ login: false, realtime: false, payments: false, fileUpload: false, ai: false })

function markCapabilitiesAnswered() {
  capabilitiesAnswered.value = true
}

function submit() {
  emit('submit', {
    projectId: props.projectId,
    knownTechnologies: [...selectedTechnologies.value],
    customTechnology: customTechnology.value.trim() || null,
    targetPlatform: targetPlatform.value || null,
    teamSize: teamSize.value || null,
    userScale: userScale.value || null,
    criticalCapabilities: capabilitiesAnswered.value ? { ...capabilities } : null,
    dataSensitivity: dataSensitivity.value || null,
    deployment: deployment.value || null,
    budget: budget.value || null,
    timeline: timeline.value || null,
    maintenanceCapacity: maintenanceCapacity.value || null,
  })
}
</script>

<template>
  <form class="constraints" aria-label="技术约束" @submit.prevent="submit">
    <header><span>步骤 1</span><h1>先确认你的技术边界</h1><p>可以部分提交；未回答的关键项会保留为待确认，不会由系统擅自补全。</p></header>

    <fieldset><legend>已掌握或愿意使用的技术</legend><div class="choices">
      <label v-for="technology in technologies" :key="technology"><input v-model="selectedTechnologies" type="checkbox" name="technology" :value="technology">{{ technology }}</label>
    </div><label class="field">自定义技术<input v-model="customTechnology" name="customTechnology" placeholder="例如 Svelte、Go"></label></fieldset>

    <div class="grid">
      <label class="field">目标终端<select v-model="targetPlatform" name="targetPlatform"><option value="">待确认</option><option value="WEB">Web</option><option value="DESKTOP">桌面端</option><option value="MOBILE">移动端</option><option value="CROSS_PLATFORM">跨端</option><option value="API">API 服务</option></select></label>
      <label class="field">团队规模<select v-model="teamSize" name="teamSize"><option value="">待确认</option><option value="SOLO">个人</option><option value="SMALL_TEAM">小团队</option><option value="LARGE_TEAM">较大团队</option></select></label>
      <label class="field">预计用户量<select v-model="userScale" name="userScale"><option value="">待确认</option><option value="PROTOTYPE">原型验证</option><option value="SMALL">小规模</option><option value="MEDIUM">中等规模</option><option value="LARGE">大规模</option></select></label>
      <label class="field">数据敏感程度<select v-model="dataSensitivity" name="dataSensitivity"><option value="">待确认</option><option value="PUBLIC">公开数据</option><option value="INTERNAL">内部数据</option><option value="PERSONAL">个人信息</option><option value="SENSITIVE">敏感数据</option><option value="HIGHLY_SENSITIVE">高度敏感</option></select></label>
      <label class="field">部署方式<select v-model="deployment" name="deployment"><option value="">待确认</option><option value="LOCAL">仅本地</option><option value="MONOLITHIC_DOCKER">单体 Docker</option><option value="CLOUD">云部署</option><option value="MULTI_INSTANCE">多实例</option></select></label>
      <label class="field">预算<select v-model="budget" name="budget"><option value="">待确认</option><option value="MINIMAL">尽量零成本</option><option value="LIMITED">有限预算</option><option value="FLEXIBLE">预算灵活</option></select></label>
      <label class="field">开发周期<select v-model="timeline" name="timeline"><option value="">待确认</option><option value="RAPID">快速原型</option><option value="STANDARD">标准周期</option><option value="LONG_TERM">长期建设</option></select></label>
      <label class="field">维护能力<select v-model="maintenanceCapacity" name="maintenanceCapacity"><option value="">待确认</option><option value="LOW">希望尽量简单</option><option value="MEDIUM">可维护常规服务</option><option value="HIGH">可维护复杂基础设施</option></select></label>
    </div>

    <fieldset><legend>关键能力</legend><p class="hint">勾选任一项即表示已经确认本组；若全部不需要，请勾选“以上均不需要”。</p><div class="choices">
      <label><input v-model="capabilities.login" name="login" type="checkbox" @change="markCapabilitiesAnswered">登录</label>
      <label><input v-model="capabilities.realtime" name="realtime" type="checkbox" @change="markCapabilitiesAnswered">实时通信</label>
      <label><input v-model="capabilities.payments" name="payments" type="checkbox" @change="markCapabilitiesAnswered">支付</label>
      <label><input v-model="capabilities.fileUpload" name="fileUpload" type="checkbox" @change="markCapabilitiesAnswered">文件</label>
      <label><input v-model="capabilities.ai" name="ai" type="checkbox" @change="markCapabilitiesAnswered">AI</label>
      <label><input name="noCapabilities" type="checkbox" @change="markCapabilitiesAnswered">以上均不需要</label>
    </div></fieldset>

    <button class="button-primary" type="submit" :disabled="busy">{{ busy ? '正在生成…' : '生成架构候选' }}</button>
  </form>
</template>

<style scoped>
.constraints{display:grid;gap:18px}.constraints header span{font-size:11px;font-weight:750;color:var(--color-accent)}.constraints h1{margin:4px 0;font-size:22px}.constraints header p,.hint{margin:0;color:var(--color-text-secondary);font-size:12px;line-height:1.6}fieldset{margin:0;padding:15px;border:1px solid var(--color-border);border-radius:10px;background:var(--color-surface)}legend{padding:0 7px;font-size:12px;font-weight:700}.choices{display:flex;flex-wrap:wrap;gap:10px 16px}.choices label{font-size:12px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.field{display:grid;gap:6px;color:var(--color-text-secondary);font-size:11px}.field input,.field select{min-height:38px;padding:0 10px;border:1px solid var(--color-border);border-radius:7px;background:var(--color-surface);color:var(--color-text-primary)}fieldset .field{margin-top:12px}.button-primary{justify-self:start;padding:10px 18px}
</style>
