import { describe, expect, it } from 'vitest'
import { LABEL_VARIANTS, labelVariant } from './labelVariant'

describe('labelVariant', () => {
  it('always gives the same colour for the same name', () => {
    expect(labelVariant('frontend')).toBe(labelVariant('frontend'))
  })

  it('ignores case and surrounding spaces, like the API', () => {
    expect(labelVariant(' Bug ')).toBe(labelVariant('bug'))
  })

  it('only uses Astryx Badge colour variants', () => {
    for (const name of ['frontend', 'backend', 'infra', 'a', '', 'ünïcode', 'x'.repeat(50)])
      expect(LABEL_VARIANTS).toContain(labelVariant(name))
  })

  it('spreads names across the variants', () => {
    const names = Array.from({ length: 20 }, (_, i) => `label-${i}`)
    expect(new Set(names.map(labelVariant)).size).toBeGreaterThanOrEqual(4)
  })
})
