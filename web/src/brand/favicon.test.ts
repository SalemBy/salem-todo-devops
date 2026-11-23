/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// Relative to web/, where Vitest runs. The tab icon cannot read CSS variables, so it mirrors the tokens.
const favicon = readFileSync('public/favicon.svg', 'utf8').toLowerCase()
const tokens = readFileSync('src/styles/tokens.css', 'utf8').toLowerCase()
const darkStart = tokens.indexOf('@media (prefers-color-scheme: dark)')

function token(name: string, theme: 'light' | 'dark'): string {
  const block = theme === 'light' ? tokens.slice(0, darkStart) : tokens.slice(darkStart)
  const value = new RegExp(`${name}:\\s*(#[0-9a-f]{6})`).exec(block)?.[1]
  if (!value) throw new Error(`${theme} theme is missing ${name}`)
  return value
}

describe('favicon.svg', () => {
  it('uses the light cat colours', () => {
    for (const name of ['--cat-body', '--cat-line', '--cat-eye'])
      expect(favicon).toContain(token(name, 'light'))
  })

  it('switches with the system theme', () => {
    const dark = favicon.slice(favicon.indexOf('@media (prefers-color-scheme: dark)'))
    expect(dark.length).toBeLessThan(favicon.length)
    for (const name of ['--cat-body', '--cat-line', '--cat-eye'])
      expect(dark).toContain(token(name, 'dark'))
  })
})
