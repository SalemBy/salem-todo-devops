import { describe, expect, it } from 'vitest'
import { apiProxy } from './proxy'

describe('apiProxy', () => {
  const entries = Object.entries(apiProxy('http://127.0.0.1:8080'))
  const [key, proxy] = entries[0] ?? []

  it('has one rule, targeting the API', () => {
    expect(entries).toHaveLength(1)
    expect(proxy?.target).toBe('http://127.0.0.1:8080')
  })

  it('matches /api and its sub-paths only', () => {
    // Vite reads a key that starts with ^ as a regular expression.
    const matches = new RegExp(key ?? '')
    expect(matches.test('/api')).toBe(true)
    expect(matches.test('/api/auth/csrf')).toBe(true)
    expect(matches.test('/api?x=1')).toBe(true)
    expect(matches.test('/apiary')).toBe(false)
    expect(matches.test('/projects/API/board')).toBe(false)
  })

  it('removes the /api prefix', () => {
    expect(proxy?.rewrite?.('/api/auth/csrf')).toBe('/auth/csrf')
    expect(proxy?.rewrite?.('/api/projects?active=true')).toBe('/projects?active=true')
    expect(proxy?.rewrite?.('/api')).toBe('/')
  })
})
