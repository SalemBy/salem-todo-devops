/// <reference types="node" />
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

// Vitest blanks CSS imports, ?raw included, so read the file itself.
const css = readFileSync('src/styles/tokens.css', 'utf8') // relative to web/, where Vitest runs

/** Reads `--name: #rrggbb;` declarations from the light block (first :root) or the dark block. */
function theme(which: 'light' | 'dark'): Map<string, string> {
  const dark = css.indexOf('@media (prefers-color-scheme: dark)')
  const block = which === 'light' ? css.slice(0, dark) : css.slice(dark)
  const tokens = new Map<string, string>()
  for (const [, name, value] of block.matchAll(/(--[\w-]+):\s*(#[0-9a-fA-F]{6})\s*;/g))
    if (name && value && !tokens.has(name)) tokens.set(name, value)
  return tokens
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }) as [number, number, number]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number]
  return (hi + 0.05) / (lo + 0.05)
}

/** [foreground, background, minimum ratio]: 4.5 for text, 3 for large text and UI boundaries. */
const PAIRS: Array<[string, string, number]> = [
  ['--ink', '--paper', 4.5],
  ['--ink', '--surface', 4.5],
  ['--ink', '--surface-sunken', 4.5],
  ['--ink-muted', '--paper', 4.5],
  ['--ink-muted', '--surface', 4.5],
  ['--ink-muted', '--surface-sunken', 4.5],
  ['--rail-ink', '--rail', 4.5],
  ['--rail-ink-muted', '--rail', 4.5],
  ['--rail-ink-strong', '--rail-active', 4.5],
  ['--accent', '--surface', 4.5],
  ['--accent', '--paper', 4.5],
  ['--accent', '--accent-soft', 4.5],
  ['--accent-ink', '--accent', 4.5],
  ['--accent-ink', '--accent-hover', 4.5],
  ['--danger', '--surface', 4.5],
  ['--danger', '--danger-soft', 4.5],
  ['--ink', '--danger-soft', 4.5],
  ['--avatar-ink', '--avatar-bg', 4.5],
  ['--avatar-edge', '--paper', 3],
  ['--danger-ink', '--danger', 4.5],
  ['--success', '--surface', 4.5],
  ['--success', '--success-soft', 4.5],
  ['--rule-strong', '--surface', 3],
  ['--rule-strong', '--paper', 3],
  ['--focus', '--surface', 3],
  ['--focus', '--paper', 3],
  ['--rail-focus', '--rail', 3],
]

describe.each(['light', 'dark'] as const)('%s theme', (which) => {
  const tokens = theme(which)
  const value = (name: string) => {
    const hex = tokens.get(name)
    if (!hex) throw new Error(`${which} theme is missing ${name}`)
    return hex
  }

  it.each(PAIRS)('%s on %s reaches %s:1', (fg, bg, min) => {
    expect(contrast(value(fg), value(bg))).toBeGreaterThanOrEqual(min)
  })

  it('every label colour is readable on its chip', () => {
    for (let n = 1; n <= 8; n++)
      expect(contrast(value(`--label-${n}-fg`), value(`--label-${n}-bg`))).toBeGreaterThanOrEqual(
        4.5,
      )
  })
})
