export function validateAnalysisModelSettings(settings: unknown): string | null {
  const value = settings as Record<string, unknown> | null | undefined
  if (!value) return '模型配置未设置，请先在「模型设置」页面配置 AI 服务。'
  if (!String(value.model ?? '').trim()) return '模型名称未填写，请先在「模型设置」页面选择模型。'
  if (value.keySource === 'USER' && !String(value.apiKey ?? '').trim()) return 'API Key 未填写，请先在「模型设置」页面输入 Key。'
  if (value.keySource !== 'SYSTEM' && value.keySource !== 'USER') return '模型 Key 来源未选择，请先在「模型设置」页面配置。'
  return null
}

export function isModelSetupErrorMessage(message: string): boolean {
  return message.includes('模型') || message.includes('API Key') || message.includes('Key 来源')
    || message.includes('Base URL') || message.includes('服务商')
}
