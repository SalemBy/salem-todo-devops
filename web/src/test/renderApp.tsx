import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { expect, vi } from 'vitest'
import { App } from '../App'
import { createQueryClient } from '../queryClient'
import { routes } from '../routes'

type Answer = (body: unknown) => Response | Promise<Response>

export const json = (status: number, body?: unknown, headers: Record<string, string> = {}) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': status < 400 ? 'application/json' : 'application/problem+json',
      ...headers,
    },
  })

export const problem = (status: number, detail: string, errors: unknown[] = [], headers = {}) =>
  json(status, { title: 'Error', status, detail, errors }, headers)

/**
 * Renders the whole app at `entry` with fetch answered per "METHOD /api/path". CSRF is answered for
 * you; any other request gets a 404. Returns the router and a log of the requests made.
 */
export function renderApp(
  entry: string | { pathname: string; search?: string; state?: unknown },
  answers: Record<string, Answer>,
) {
  const requests: Array<{ key: string; body: unknown }> = []
  vi.stubGlobal(
    'fetch',
    vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(async (input, init = {}) => {
      const key = `${init.method ?? 'GET'} ${input}`
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined
      requests.push({ key, body })
      if (key === 'GET /api/auth/csrf') return json(200, { token: 't', headerName: 'X-CSRF-TOKEN' })
      const answer = answers[key]
      return answer ? answer(body) : problem(404, 'Not found')
    }),
  )
  const router = createMemoryRouter(routes, { initialEntries: [entry] })
  const queryClient = createQueryClient()
  render(<App router={router} queryClient={queryClient} />)
  return {
    router,
    queryClient,
    requests,
    sent: (key: string) => requests.filter((r) => r.key === key),
  }
}

/** The page's one message in a live region. Astryx renders empty live regions of its own. */
export const live = (role: 'status' | 'alert') =>
  waitFor(() => {
    const found = screen.queryAllByRole(role).filter((el) => el.textContent?.trim())
    expect(found).toHaveLength(1)
    return found[0] as HTMLElement
  })
