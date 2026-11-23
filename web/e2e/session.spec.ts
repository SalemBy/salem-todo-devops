import { expect, test, type APIRequestContext } from '@playwright/test'
import { verificationToken } from './mailpit.ts'

/** A fresh token for the request client's current session, sent the way the UI's API module sends it. */
async function csrf(request: APIRequestContext): Promise<Record<string, string>> {
  const response = await request.get('/api/auth/csrf')
  expect(response.status()).toBe(200)
  const { token, headerName } = (await response.json()) as { token: string; headerName: string }
  return { [headerName]: token }
}

const letters = () =>
  Array.from({ length: 4 }, () => String.fromCharCode(65 + Math.floor(Math.random() * 26))).join('')

test('login and a write go through /api with the session cookie and csrf header', async ({
  request,
}) => {
  // The request client keeps cookies like a browser, so this is the UI's own path through the proxy.
  const email = `e2e-${Date.now()}-${letters().toLowerCase()}@example.com`
  const password = 'e2e correct horse battery staple'

  const register = await request.post('/api/auth/register', {
    headers: await csrf(request),
    data: { email, displayName: 'E2E Tester', password },
  })
  expect(register.status()).toBe(201)

  const verify = await request.post('/api/auth/verify', {
    headers: await csrf(request),
    data: { token: await verificationToken(email), password },
  })
  expect(verify.status()).toBe(204)

  const login = await request.post('/api/auth/login', {
    headers: await csrf(request),
    data: { email, password },
  })
  expect(login.status()).toBe(200)

  // Login replaces the session and its CSRF token, so the write needs a new one.
  const key = `E2E${letters()}`
  const created = await request.post('/api/projects', {
    headers: await csrf(request),
    data: { name: 'End-to-end project', key },
  })
  expect(created.status()).toBe(201)

  const projects = (await (await request.get('/api/projects')).json()) as Array<{ key: string }>
  expect(projects.map((project) => project.key)).toContain(key)

  const withoutToken = await request.post('/api/projects', {
    data: { name: 'Refused', key: `E2F${letters()}` },
  })
  expect(withoutToken.status()).toBe(403)
  expect(((await withoutToken.json()) as { detail: string }).detail).toBe('Request forbidden')
})
