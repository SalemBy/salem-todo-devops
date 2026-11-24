/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'
import { HEAD_PATH } from './CatHead'

// Relative to web/, where Vitest runs. The tab icon cannot read CSS variables, so it mirrors the tokens.
const favicon = readFileSync('public/favicon.svg', 'utf8').toLowerCase().replace(/\s+/g, ' ')
const tokens = readFileSync('src/styles/tokens.css', 'utf8').toLowerCase()
const neutral = readFileSync(
  createRequire(`${process.cwd()}/package.json`).resolve('@astryxdesign/theme-neutral/theme.css'),
  'utf8',
).toLowerCase()
const DARK = '@media (prefers-color-scheme: dark)'

/**
 * The colour a cat token has in one theme. Body and eyes are light-dark() pairs in tokens.css; the
 * outline is the Astryx Neutral theme's text colour.
 */
function token(name: string, theme: 'light' | 'dark'): string {
  const css = name === '--cat-line' ? neutral : tokens
  const source = name === '--cat-line' ? '--color-text-primary' : name
  const match = new RegExp(`${source}:\\s*light-dark\\((#[0-9a-f]{6}),\\s*(#[0-9a-f]{6})\\)`).exec(
    css,
  )
  const value = match?.[theme === 'light' ? 1 : 2]
  if (!value) throw new Error(`${theme} theme is missing ${name}`)
  return value
}

/** The favicon's style rules for one theme, read from its light part or its dark @media part. */
function rules(theme: 'light' | 'dark'): string {
  const at = favicon.indexOf(DARK)
  expect(at).toBeGreaterThan(0)
  return theme === 'light' ? favicon.slice(0, at) : favicon.slice(at)
}

describe('favicon.svg', () => {
  it.each(['light', 'dark'] as const)('paints each part with the %s cat tokens', (theme) => {
    const css = rules(theme)
    const body = token('--cat-body', theme)
    expect(css).toContain(`.body { fill: ${body}; stroke: ${token('--cat-line', theme)}; }`)
    expect(css).toContain(`.eye { fill: ${token('--cat-eye', theme)}; }`)
    expect(css).toContain(`.pupil { fill: ${body}; }`)
  })

  it('draws the same head as the mark', () => {
    expect(favicon).toContain(HEAD_PATH.toLowerCase())
  })
})
