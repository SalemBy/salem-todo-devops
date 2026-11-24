import { describe, expect, it } from 'vitest'
import { safeNext } from './safeNext'

describe('safeNext', () => {
  it('keeps a path inside the app, with its query', () => {
    expect(safeNext('/projects/SALEM/board?type=BUG')).toBe('/projects/SALEM/board?type=BUG')
  })

  it.each([
    null,
    '',
    '//evil.example',
    'https://evil.example',
    '/\\evil.example',
    'javascript:alert(1)',
    'projects',
    '/login',
    '/login?next=/x',
  ])('sends %j to all projects', (next) => {
    expect(safeNext(next)).toBe('/')
  })
})
