import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { json, live, problem, renderApp } from '../test/renderApp'

const ada = {
  id: 1,
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  emailVerified: true,
}

async function chooseLogOut() {
  await userEvent.click(await screen.findByRole('button', { name: /Account/ }))
  await userEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('log out', () => {
  it('logs out from the account menu', async () => {
    // Like the server: the account is readable until the session ends.
    let signedIn = true
    const { router, queryClient, sent } = renderApp('/', {
      'GET /api/auth/me': () =>
        signedIn ? json(200, ada) : problem(401, 'Authentication required'),
      'POST /api/auth/logout': () => {
        signedIn = false
        return json(204)
      },
    })

    await chooseLogOut()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sent('POST /api/auth/logout')).toHaveLength(1)
    expect(await live('status')).toHaveTextContent('You are logged out.')
    await waitFor(() => expect(queryClient.getQueryData(['me'])).toBeUndefined())
    expect(router.state.location.search).toBe('')
  })

  it('the account menu names the person', async () => {
    renderApp('/', { 'GET /api/auth/me': () => json(200, ada) })

    expect(await screen.findByRole('button', { name: 'Account: Ada Lovelace' })).toBeVisible()
  })

  it('late 401s after logging out stay on login', async () => {
    const { router } = renderApp('/', {
      'GET /api/auth/me': () => json(200, ada),
      'POST /api/auth/logout': () => json(204),
      'GET /api/projects': () => problem(401, 'Authentication required'),
    })
    await chooseLogOut()
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))

    await api.get('/projects').catch(() => undefined)

    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('')
  })

  it('a failed log out keeps the person signed in and says so', async () => {
    const { router } = renderApp('/', {
      'GET /api/auth/me': () => json(200, ada),
      'POST /api/auth/logout': () => problem(503, 'Service unavailable'),
    })

    await chooseLogOut()

    // Astryx shows the toast in a native popover (which jsdom never shows) and also announces it.
    const told = await screen.findAllByText('Could not log out. Try again in a moment.')
    expect(told.some((el) => el.closest('[popover]'))).toBe(true)
    expect(router.state.location.pathname).toBe('/')
  })

  it('logging out after the session already ended still says logged out', async () => {
    // The session ended before the click (idle, or revoked elsewhere); the page has not noticed yet.
    let ended = false
    const { router, queryClient } = renderApp('/', {
      'GET /api/auth/me': () => (ended ? problem(401, 'Authentication required') : json(200, ada)),
      'POST /api/auth/logout': () => {
        ended = true
        return problem(401, 'Authentication required')
      },
    })

    await chooseLogOut()

    expect(await live('status')).toHaveTextContent('You are logged out.')
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('')
    // No toast. (Astryx's page-wide announcer keeps the previous test's words, so look at toasts only.)
    const toasts = screen.queryAllByText('Could not log out. Try again in a moment.')
    expect(toasts.filter((el) => el.closest('[popover]'))).toEqual([])
    await waitFor(() => expect(queryClient.getQueryData(['me'])).toBeUndefined())
  })
})
