import { describe, expect, it } from 'vitest'
import { validateMermaid } from './mermaidValidator'

describe('validateMermaid', () => {
  it('accepts a legal flowchart', async () => {
    await expect(validateMermaid('flowchart TD\nA[开始] --> B[结束]')).resolves.toMatchObject({ valid: true })
  })

  it('rejects blank and illegal syntax', async () => {
    await expect(validateMermaid('   ')).resolves.toMatchObject({ valid: false })
    await expect(validateMermaid('this is not mermaid')).resolves.toMatchObject({ valid: false })
  })
})
