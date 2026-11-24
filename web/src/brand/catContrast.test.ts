/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'

// Relative to web/, where Vitest runs.
const tokens = readFileSync('src/styles/tokens.css', 'utf8')
const neutral = readFileSync(
  createRequire(`${process.cwd()}/package.json`).resolve('@astryxdesign/theme-neutral/theme.css'),
  'utf8',
)

/** The [light, dark] pair a theme declares as `--name: light-dark(#light, #dark)`. */
function pair(css: string, name: string): [string, string] {
  const match = new RegExp(
    `${name}:\\s*light-dark\\((#[0-9a-fA-F]{6}),\\s*(#[0-9a-fA-F]{6})\\)`,
  ).exec(css)
  if (!match?.[1] || !match[2]) throw new Error(`${name} has no light-dark() pair`)
  return [match[1], match[2]]
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }) as [number, number, number]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number]
  return (hi + 0.05) / (lo + 0.05)
}

describe('Salem on the Astryx Neutral theme', () => {
  it('outlines the cat with the theme text colour, where the theme defines it', () => {
    expect(tokens).toMatch(/\[data-astryx-theme\][^}]*--cat-line:\s*var\(--color-text-primary\)/)
  })

  it.each([0, 1])(
    'the outline stands out on every theme background (%i: 0 light, 1 dark)',
    (mode) => {
      const line = pair(neutral, '--color-text-primary')[mode] ?? ''
      for (const background of [
        '--color-background-body',
        '--color-background-surface',
        '--color-background-muted',
      ])
        expect(contrast(line, pair(neutral, background)[mode] ?? '')).toBeGreaterThanOrEqual(3)
    },
  )

  it.each([0, 1])('the eyes stand out on the black body (%i: 0 light, 1 dark)', (mode) => {
    expect(
      contrast(pair(tokens, '--cat-eye')[mode] ?? '', pair(tokens, '--cat-body')[mode] ?? ''),
    ).toBeGreaterThanOrEqual(3)
  })
})
