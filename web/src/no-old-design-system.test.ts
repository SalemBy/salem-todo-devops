/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/** Every source file under src (relative to web/, where Vitest runs), except Salem's own drawings. */
function sources(dir = 'src'): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sources(path)
    return /\.(tsx?|css)$/.test(name) ? [path.replaceAll('\\', '/')] : []
  })
}

const files = sources().filter(
  (path) =>
    !path.startsWith('src/brand/') &&
    path !== 'src/styles/tokens.css' &&
    !path.endsWith('no-old-design-system.test.ts'),
)

describe('the old design system is gone', () => {
  it.each(files)('%s imports none of its components or stylesheets', (path) => {
    const text = readFileSync(path, 'utf8')
    expect(text).not.toMatch(
      /components\/(Button|TextField|Select|Menu|Dialog|Badge|LabelChip|Avatar|EmptyState|ErrorMessage|Toast)\//,
    )
    expect(text).not.toMatch(/styles\/global\.css|\.module\.css/)
  })

  it.each(files)('%s uses none of its tokens', (path) => {
    expect(readFileSync(path, 'utf8')).not.toMatch(
      /var\(--(ink|paper|rail|accent|surface|rule|danger|success|grid|space-\d|text-(xs|sm|md|lg|xl))\b/,
    )
  })
})
