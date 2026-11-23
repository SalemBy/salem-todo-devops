import { expect, type APIRequestContext } from '@playwright/test'
import { verificationToken } from './mailpit.ts'

/** A fresh token for the request client's current session, sent the way the UI's API module sends it. */
export async function csrf(request: APIRequestContext): Promise<Record<string, string>> {
  const response = await request.get('/api/auth/csrf')
  expect(response.status()).toBe(200)
  const { token, headerName } = (await response.json()) as { token: string; headerName: string }
  return { [headerName]: token }
}

export const letters = () =>
  Array.from({ length: 4 }, () => String.fromCharCode(65 + Math.floor(Math.random() * 26))).join('')

/** Registers, verifies and signs in a new account on this request client's session. */
export async function signIn(request: APIRequestContext, displayName = 'E2E Tester') {
  const email = `e2e-${Date.now()}-${letters().toLowerCase()}@example.com`
  const password = 'e2e correct horse battery staple'
  const post = async (path: string, data: object) =>
    request.post(path, { headers: await csrf(request), data })

  expect((await post('/api/auth/register', { email, displayName, password })).status()).toBe(201)
  const token = await verificationToken(email)
  expect((await post('/api/auth/verify', { token, password })).status()).toBe(204)
  expect((await post('/api/auth/login', { email, password })).status()).toBe(200)
  return { email, password }
}
