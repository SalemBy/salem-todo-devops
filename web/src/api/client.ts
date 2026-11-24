import type { CsrfResponse } from './types'

/** The only place the UI talks to the API. Callers pass API paths such as `/projects`. */

const BASE = '/api'
/** A 401 from these means wrong credentials or a bad link, not an expired session. */
const PUBLIC_AUTH = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/verify',
  '/auth/verification-email',
])
/** These replace or end the session, and with it the CSRF token. */
const SESSION_CHANGING = new Set(['/auth/login', '/auth/logout', '/auth/password'])
/** Spring Security's detail for every CSRF failure; business refusals use other details. */
const STALE_CSRF = 'Request forbidden'

export interface FieldError {
  field: string
  message: string
}

/** An error response from the API (`application/problem+json`), or status 0 when it was not reached. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string
  readonly errors: FieldError[]
  /** Seconds to wait before trying again, from a 429's Retry-After header. */
  readonly retryAfter: number | undefined

  constructor(
    status: number,
    title: string,
    detail: string,
    errors: FieldError[] = [],
    retryAfter?: number,
  ) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.errors = errors
    this.retryAfter = retryAfter
  }
}

// In memory only: never in browser storage.
let csrf: Promise<CsrfResponse> | null = null
let onUnauthorized: () => void = () => {}

/** Called on a 401 from a business route, before the request rejects. */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

function token(): Promise<CsrfResponse> {
  if (!csrf) {
    const pending = send<CsrfResponse>('GET', '/auth/csrf')
    csrf = pending
    // Drop a failed fetch so the next write tries again instead of reusing the rejection.
    pending.catch(() => {
      if (csrf === pending) csrf = null
    })
  }
  return csrf
}

async function send<T>(method: string, path: string, body?: unknown, retried = false): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    // The UI is in English; without this the API words its validation messages in the browser's language.
    'Accept-Language': 'en',
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const { token: value, headerName } = await token()
    headers[headerName] = value
  }

  let response: Response
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(
      0,
      'Network error',
      'The server could not be reached. Check your connection.',
    )
  }

  const route = path.split('?')[0] ?? path
  if (response.ok) {
    if (SESSION_CHANGING.has(route)) csrf = null
    return (await readBody(response)) as T
  }

  const error = await toError(response)
  if (error.status === 403 && error.detail === STALE_CSRF && method !== 'GET' && !retried) {
    csrf = null
    return send<T>(method, path, body, true)
  }
  if (error.status === 401 && !PUBLIC_AUTH.has(route)) {
    csrf = null // the token belonged to the session that just ended
    onUnauthorized()
  }
  throw error
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  return text === '' ? undefined : JSON.parse(text)
}

async function toError(response: Response): Promise<ApiError> {
  const header = response.headers.get('Retry-After') ?? ''
  const retryAfter = /^\d+$/.test(header) ? Number(header) : undefined
  const fallback = new ApiError(
    response.status,
    response.statusText || 'Error',
    `The server sent an unexpected response (${response.status}). Try again in a moment.`,
    [],
    retryAfter,
  )
  const type = response.headers.get('Content-Type') ?? ''
  if (!type.includes('json')) return fallback
  try {
    const problem = (await response.json()) as Partial<{
      title: string
      detail: string
      errors: FieldError[]
    }>
    return new ApiError(
      response.status,
      problem.title ?? fallback.title,
      problem.detail ?? fallback.detail,
      Array.isArray(problem.errors) ? problem.errors : [],
      retryAfter,
    )
  } catch {
    return fallback
  }
}

export const api = {
  get: <T>(path: string) => send<T>('GET', path),
  post: <T>(path: string, body?: unknown) => send<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => send<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => send<T>('PATCH', path, body),
  delete: <T = void>(path: string) => send<T>('DELETE', path),
}
