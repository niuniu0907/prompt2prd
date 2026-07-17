import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TechnicalConstraintsForm from './TechnicalConstraintsForm.vue'

const projectId = '123e4567-e89b-42d3-a456-426614174000'

describe('TechnicalConstraintsForm', () => {
  it('submits complete constraints including custom technology and sensitive data', async () => {
    const wrapper = mount(TechnicalConstraintsForm, { props: { projectId } })
    await wrapper.find('input[value="Vue 3"]').setValue(true)
    await wrapper.find('input[name="customTechnology"]').setValue('Go')
    for (const [name, value] of Object.entries({ targetPlatform: 'WEB', teamSize: 'SOLO', userScale: 'SMALL', dataSensitivity: 'SENSITIVE', deployment: 'MONOLITHIC_DOCKER', budget: 'MINIMAL', timeline: 'RAPID', maintenanceCapacity: 'LOW' })) {
      await wrapper.find(`select[name="${name}"]`).setValue(value)
    }
    await wrapper.find('input[name="ai"]').setValue(true)
    await wrapper.find('form').trigger('submit')

    const payload = wrapper.emitted('submit')?.[0]?.[0] as Record<string, unknown>
    expect(payload).toMatchObject({ projectId, knownTechnologies: ['Vue 3'], customTechnology: 'Go', dataSensitivity: 'SENSITIVE' })
    expect(payload.criticalCapabilities).toMatchObject({ ai: true })
  })

  it('keeps unanswered critical groups null on partial submit', async () => {
    const wrapper = mount(TechnicalConstraintsForm, { props: { projectId } })
    await wrapper.find('input[name="customTechnology"]').setValue('Svelte')
    await wrapper.find('form').trigger('submit')
    const payload = wrapper.emitted('submit')?.[0]?.[0] as Record<string, unknown>
    expect(payload).toMatchObject({ customTechnology: 'Svelte', targetPlatform: null, teamSize: null, criticalCapabilities: null })
  })
})
