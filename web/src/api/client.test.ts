import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type * as ClientModule from './client'

type Client = typeof ClientModule

interface Call {
  method: string
  url: string
  headers: Headers
  body: unknown
}

const json = (status: number, body: unknown, type = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })
const problem = (status: number, detail: string, errors: unknown[] = []) =>
  json(status, { title: 'Error', status, detail, errors }, 'application/problem+json')
const csrfToken = (token: string) => json(200, { token, headerName: 'X-CSRF-TOKEN' })

/** A fake fetch that answers each "METHOD /url" from its own queue and records every call. */
function fakeFetch(routes: Record<string, Array<() => Response>>) {
  const calls: Call[] = []
  const fetch = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(
    async (input, init = {}) => {
      const method = init.method ?? 'GET'
      calls.push({
        method,
        url: input,
        headers: new Headers(init.headers),
        body: typeof init.body === 'string' ? JSON.parse(init.body) : init.body,
      })
      const next = routes[`${method} ${input}`]?.shift()
      if (!next) throw new Error(`Unexpected request ${method} ${input}`)
      return next()
    },
  )
  vi.stubGlobal('fetch', fetch)
  return {
    calls,
    count: (key: string) => calls.filter((c) => `${c.method} ${c.url}` === key).length,
  }
}

let client: Client

beforeEach(async () => {
  vi.resetModules()
  client = await import('./client')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('csrf', () => {
  it('get does not fetch a csrf token', async () => {
    const http = fakeFetch({ 'GET /api/projects': [() => json(200, [])] })

    await client.api.get('/projects')

    expect(http.calls).toHaveLength(1)
    expect(http.calls[0]?.headers.has('X-CSRF-TOKEN')).toBe(false)
  })

  it('writes send the csrf header from /auth/csrf', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'POST /api/projects': [() => json(201, { id: 1 }), () => json(201, { id: 2 })],
    })

    await client.api.post('/projects', { name: 'A', key: 'AA' })
    await client.api.post('/projects', { name: 'B', key: 'BB' })

    const writes = http.calls.filter((c) => c.method === 'POST')
    expect(writes.map((c) => c.headers.get('X-CSRF-TOKEN'))).toEqual(['t1', 't1'])
    expect(writes[0]?.headers.get('Content-Type')).toBe('application/json')
    expect(writes[0]?.body).toEqual({ name: 'A', key: 'AA' })
    expect(http.count('GET /api/auth/csrf')).toBe(1)
  })

  it('concurrent first writes share one token fetch', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'POST /api/projects': [() => json(201, {}), () => json(201, {})],
    })

    await Promise.all([client.api.post('/projects', {}), client.api.post('/projects', {})])

    expect(http.count('GET /api/auth/csrf')).toBe(1)
  })

  it('a failed token fetch is not cached', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => problem(503, 'Service unavailable'), () => csrfToken('t2')],
      'POST /api/projects': [() => json(201, {})],
    })

    await expect(client.api.post('/projects', {})).rejects.toMatchObject({ status: 503 })
    await client.api.post('/projects', {})

    expect(http.count('GET /api/auth/csrf')).toBe(2)
    expect(http.calls.at(-1)?.headers.get('X-CSRF-TOKEN')).toBe('t2')
  })

  it.each([
    ['/auth/login', () => json(200, { id: 1 })],
    ['/auth/logout', () => new Response(null, { status: 204 })],
    ['/auth/password', () => new Response(null, { status: 204 })],
  ])('%s drops the token', async (path, answer) => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1'), () => csrfToken('t2')],
      [`POST /api${path}`]: [answer],
      'POST /api/projects': [() => json(201, {})],
    })

    await client.api.post(path, {})
    await client.api.post('/projects', {})

    expect(http.count('GET /api/auth/csrf')).toBe(2)
    expect(http.calls.at(-1)?.headers.get('X-CSRF-TOKEN')).toBe('t2')
  })

  it('stale csrf 403 refreshes the token and retries once', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1'), () => csrfToken('t2')],
      'PUT /api/projects/1': [() => problem(403, 'Request forbidden'), () => json(200, { id: 1 })],
    })

    await expect(client.api.put('/projects/1', { name: 'New' })).resolves.toEqual({ id: 1 })

    const writes = http.calls.filter((c) => c.method === 'PUT')
    expect(writes.map((c) => c.headers.get('X-CSRF-TOKEN'))).toEqual(['t1', 't2'])
    expect(writes[1]?.body).toEqual({ name: 'New' })
  })

  it('a second stale csrf 403 is not retried again', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1'), () => csrfToken('t2')],
      'PUT /api/projects/1': [
        () => problem(403, 'Request forbidden'),
        () => problem(403, 'Request forbidden'),
      ],
    })

    await expect(client.api.put('/projects/1', {})).rejects.toMatchObject({
      status: 403,
      detail: 'Request forbidden',
    })
    expect(http.calls).toHaveLength(4)
  })

  it('a permission 403 is not retried', async () => {
    const http = fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'DELETE /api/projects/1': [
        () => problem(403, 'Your project role does not allow this action'),
      ],
    })

    const error = await client.api.delete('/projects/1').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(client.ApiError)
    expect(error).toMatchObject({
      status: 403,
      detail: 'Your project role does not allow this action',
    })
    expect(http.calls).toHaveLength(2)
  })

  it('the token is never written to browser storage', async () => {
    fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'POST /api/projects': [() => json(201, {})],
    })

    await client.api.post('/projects', {})

    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
})

describe('responses', () => {
  it('problem+json field errors are parsed', async () => {
    const errors = [{ field: 'name', message: 'must not be blank' }]
    fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'POST /api/projects': [
        () =>
          json(
            400,
            { title: 'Bad Request', status: 400, detail: 'Request validation failed', errors },
            'application/problem+json',
          ),
      ],
    })

    const error = await client.api.post('/projects', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(client.ApiError)
    expect(error).toMatchObject({
      status: 400,
      title: 'Bad Request',
      detail: 'Request validation failed',
      errors,
    })
  })

  it('a problem without errors gives an empty list', async () => {
    fakeFetch({
      'GET /api/projects/9': [
        () =>
          json(
            404,
            { title: 'Not Found', detail: 'Project not found' },
            'application/problem+json',
          ),
      ],
    })

    await expect(client.api.get('/projects/9')).rejects.toMatchObject({
      status: 404,
      detail: 'Project not found',
      errors: [],
    })
  })

  it.each([
    [
      'an html page',
      () =>
        new Response('<html>Bad gateway</html>', {
          status: 502,
          statusText: 'Bad Gateway',
          headers: { 'Content-Type': 'text/html' },
        }),
      502,
    ],
    ['an empty body', () => new Response(null, { status: 500 }), 500],
    [
      'broken json',
      () =>
        new Response('{', { status: 500, headers: { 'Content-Type': 'application/problem+json' } }),
      500,
    ],
  ])('non-json errors still give an ApiError: %s', async (_, answer, status) => {
    fakeFetch({ 'GET /api/projects': [answer] })

    const error = await client.api.get('/projects').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(client.ApiError)
    expect(error).toMatchObject({ status, errors: [] })
    expect((error as InstanceType<Client['ApiError']>).detail).not.toBe('')
  })

  it('a network failure gives an ApiError with status 0', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<() => Promise<Response>>(async () => Promise.reject(new TypeError('Failed to fetch'))),
    )

    await expect(client.api.get('/projects')).rejects.toMatchObject({ status: 0 })
  })

  it('204 resolves undefined', async () => {
    fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'DELETE /api/projects/1': [() => new Response(null, { status: 204 })],
    })

    await expect(client.api.delete('/projects/1')).resolves.toBeUndefined()
  })

  it('sends cookies with every request', async () => {
    fakeFetch({ 'GET /api/auth/me': [() => json(200, { id: 1 })] })

    await client.api.get('/auth/me')

    expect(vi.mocked(fetch).mock.calls[0]?.[1]?.credentials).toBe('same-origin')
  })
})

describe('unauthorized', () => {
  it('401 on a business route calls the unauthorized handler', async () => {
    const handler = vi.fn<() => void>()
    client.setUnauthorizedHandler(handler)
    fakeFetch({ 'GET /api/projects?active=true': [() => problem(401, 'Authentication required')] })

    await expect(client.api.get('/projects?active=true')).rejects.toMatchObject({ status: 401 })
    expect(handler).toHaveBeenCalledOnce()
  })

  it('401 from /auth/login does not call the handler', async () => {
    const handler = vi.fn<() => void>()
    client.setUnauthorizedHandler(handler)
    fakeFetch({
      'GET /api/auth/csrf': [() => csrfToken('t1')],
      'POST /api/auth/login': [() => problem(401, 'Invalid credentials')],
    })

    await expect(
      client.api.post('/auth/login', { email: 'a@b.c', password: 'x' }),
    ).rejects.toMatchObject({
      status: 401,
      detail: 'Invalid credentials',
    })
    expect(handler).not.toHaveBeenCalled()
  })
})
