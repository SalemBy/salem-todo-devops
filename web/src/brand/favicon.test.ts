/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { HEAD_PATH } from './CatHead'

// Relative to web/, where Vitest runs. The tab icon cannot read CSS variables, so it mirrors the tokens.
const favicon = readFileSync('public/favicon.svg', 'utf8').toLowerCase().replace(/\s+/g, ' ')
const tokens = readFileSync('src/styles/tokens.css', 'utf8').toLowerCase()
const DARK = '@media (prefers-color-scheme: dark)'

function token(name: string, theme: 'light' | 'dark'): string {
  const at = tokens.indexOf(DARK)
  const block = theme === 'light' ? tokens.slice(0, at) : tokens.slice(at)
  const value = new RegExp(`${name}:\\s*(#[0-9a-f]{6})`).exec(block)?.[1]
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
