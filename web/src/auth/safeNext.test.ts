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
    // The URL parser drops tabs and newlines, so these are other hosts to a browser.
    '/\t/evil.example',
    '/\n/evil.example',
    '/\t\\evil.example',
    // Other spellings of the login page.
    '/login/',
    '/LOGIN',
    '/../login',
    '/x/../login?a=1',
  ])('sends %j to all projects', (next) => {
    expect(safeNext(next)).toBe('/')
  })
})
