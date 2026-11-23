import { describe, expect, it } from 'vitest'
import { LABEL_COLORS, labelColor } from './labelColor'

describe('labelColor', () => {
  it('always gives the same colour for the same name', () => {
    expect(labelColor('frontend')).toBe(labelColor('frontend'))
  })

  it('ignores case and surrounding spaces, like the API', () => {
    expect(labelColor(' Bug ')).toBe(labelColor('bug'))
  })

  it('only uses the label palette', () => {
    for (const name of ['frontend', 'backend', 'infra', 'a', '', 'ünïcode', 'x'.repeat(50)])
      expect(LABEL_COLORS).toContain(labelColor(name))
  })

  it('spreads names across the palette', () => {
    const names = Array.from({ length: 20 }, (_, i) => `label-${i}`)
    expect(new Set(names.map(labelColor)).size).toBeGreaterThanOrEqual(4)
  })
})
