import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter } from 'react-router'
import { api } from './api/client'
import { App } from './App'
import { createQueryClient } from './queryClient'
import { routes } from './routes'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status < 400 ? 'application/json' : 'application/problem+json' },
  })
const unauthorized = () => json(401, { title: 'Unauthorized', detail: 'Authentication required' })
const ada = {
  id: 1,
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  emailVerified: true,
}

function setup(path: string, me: () => Response, retry = true) {
  vi.stubGlobal(
    'fetch',
    vi.fn<(input: string) => Promise<Response>>(async (input) =>
      input === '/api/auth/me' ? me() : unauthorized(),
    ),
  )
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = createQueryClient()
  if (!retry) queryClient.setDefaultOptions({ queries: { retry: false } })
  const navigate = vi.spyOn(router, 'navigate')
  render(<App router={router} queryClient={queryClient} />)
  return { router, queryClient, navigate }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('App', () => {
  it('sends an anonymous visitor to login with the page to return to', async () => {
    const { router } = setup('/projects/SALEM/board?type=BUG', unauthorized)

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeVisible()
    expect(screen.getByRole('main')).toContainElement(
      screen.getByRole('heading', { name: 'Log in' }),
    )
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?next=%2Fprojects%2FSALEM%2Fboard%3Ftype%3DBUG')
  })

  it('shows the signed-in account the shell', async () => {
    setup('/', () => json(200, ada))

    expect(await screen.findByRole('navigation', { name: 'Side navigation' })).toBeVisible()
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Ada Lovelace' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Skip to content' })).toBeInTheDocument()
  })

  it('nav links route inside the app instead of loading a new page', async () => {
    const { router } = setup('/no-such-page', () => json(200, ada))

    await userEvent.click(await screen.findByRole('link', { name: 'All projects' }))

    // jsdom cannot load a new page, so only the router moving proves the link went through it.
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('names the product once in the rail', async () => {
    setup('/', () => json(200, ada))

    const nav = await screen.findByRole('navigation', { name: 'Side navigation' })
    expect(within(nav).getAllByText('Salem Todo DevOps')).toHaveLength(1)
    expect(nav.querySelector('svg[data-mark]')).toHaveAttribute('aria-hidden', 'true')
  })

  it('clears cached data on a 401', async () => {
    const { queryClient } = setup('/projects', unauthorized)
    queryClient.setQueryData(['projects'], [{ id: 1 }])

    await screen.findByRole('heading', { name: 'Log in' })

    expect(queryClient.getQueryData(['projects'])).toBeUndefined()
  })

  it('redirects once when several requests fail with 401', async () => {
    const { router, navigate } = setup('/', () => json(200, ada))
    await screen.findByRole('navigation', { name: 'Side navigation' })

    await Promise.allSettled([api.get('/projects'), api.get('/projects?active=false')])

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(navigate).toHaveBeenCalledOnce()
    expect(router.state.location.search).toBe('?next=%2F')
  })

  it('does not redirect again from the login page', async () => {
    const { navigate } = setup('/login', unauthorized)
    await screen.findByRole('heading', { name: 'Log in' })

    await api.get('/projects').catch(() => undefined)

    expect(navigate).not.toHaveBeenCalled()
  })

  it('shows not found for an unknown page', async () => {
    setup('/no-such-page', () => json(200, ada))

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeVisible()
  })

  it('shows an error with a retry when the account cannot be loaded', async () => {
    setup(
      '/',
      () => json(503, { title: 'Service Unavailable', detail: 'Session store unavailable' }),
      false,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Session store unavailable')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
    expect(screen.getByRole('main')).toContainElement(screen.getByRole('alert'))
  })
})
