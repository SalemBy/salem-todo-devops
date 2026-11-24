import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { createQueryClient } from '../queryClient'
import { routes } from '../routes'

const ada = {
  id: 1,
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  active: true,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  emailVerified: true,
}
const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': status < 400 ? 'application/json' : 'application/problem+json',
      ...headers,
    },
  })
const csrf = () => json(200, { token: 't', headerName: 'X-CSRF-TOKEN' })

type Answer = () => Response | Promise<Response>

function setup(
  entry: string | { pathname: string; search?: string; state?: unknown },
  login: Answer,
) {
  const fetch = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(async (input) => {
    if (input === '/api/auth/csrf') return csrf()
    if (input === '/api/auth/login') return login()
    if (input === '/api/auth/me') return json(200, ada)
    return json(404, { title: 'Not Found', detail: 'Not found' })
  })
  vi.stubGlobal('fetch', fetch)
  const router = createMemoryRouter(routes, { initialEntries: [entry] })
  const queryClient = createQueryClient()
  render(<App router={router} queryClient={queryClient} />)
  const loginCalls = () => fetch.mock.calls.filter(([url]) => url === '/api/auth/login').length
  return { router, queryClient, loginCalls }
}

async function fillAndSubmit() {
  await userEvent.type(await screen.findByLabelText('Email'), 'ada@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'a long enough password')
  await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

/** Astryx renders empty live regions of its own; the page's message is the one with words in it. */
const live = (role: 'status' | 'alert') =>
  waitFor(() => {
    const found = screen.queryAllByRole(role).filter((el) => el.textContent?.trim())
    expect(found).toHaveLength(1)
    return found[0] as HTMLElement
  })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('LoginPage', () => {
  it('logs in and returns to the page the person was on', async () => {
    const { router, queryClient } = setup('/login?next=%2Fprojects%2FSALEM%2Fboard', () =>
      json(200, ada),
    )

    await fillAndSubmit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/projects/SALEM/board'))
    expect(queryClient.getQueryData(['me'])).toEqual(ada)
  })

  it('a crafted next lands on all projects', async () => {
    const { router } = setup('/login?next=%2F%2Fevil.example', () => json(200, ada))

    await fillAndSubmit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('every 401 shows the same message', async () => {
    setup('/login', () => json(401, { title: 'Unauthorized', detail: 'Invalid credentials' }))

    await fillAndSubmit()

    expect(await live('alert')).toHaveTextContent(
      'Invalid email or password. If you registered recently, open the link in your verification email first.',
    )
  })

  it('429 says when to retry', async () => {
    setup('/login', () =>
      json(
        429,
        { title: 'Too Many Requests', detail: 'Too many requests. Try again later.' },
        {
          'Retry-After': '120',
        },
      ),
    )

    await fillAndSubmit()

    expect(await live('alert')).toHaveTextContent(
      'Too many sign-in attempts. Try again in 2 minutes.',
    )
  })

  it('503 says it is temporary', async () => {
    setup('/login', () =>
      json(503, {
        title: 'Service Unavailable',
        detail: 'Authentication is temporarily unavailable.',
      }),
    )

    await fillAndSubmit()

    expect(await live('alert')).toHaveTextContent(
      'Signing in is unavailable right now. Try again in a moment.',
    )
  })

  it('asks for the email and password without sending anything', async () => {
    const { loginCalls } = setup('/login', () => json(200, ada))

    await userEvent.click(await screen.findByRole('button', { name: 'Log in' }))

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Enter your email.')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password.')
    expect(loginCalls()).toBe(0)
  })

  it('says where logging in will return to', async () => {
    setup('/login?next=%2Fprojects%2FSALEM%2Fboard', () => json(200, ada))

    expect(await live('status')).toHaveTextContent('Log in to go back to the page you were on.')
  })

  it('says nothing about returning when there is nowhere to return to', async () => {
    setup('/login?next=%2F', () => json(200, ada))

    await screen.findByRole('heading', { name: 'Log in' })
    expect(screen.queryAllByRole('status').filter((el) => el.textContent?.trim())).toEqual([])
  })

  it('shows the verified notice', async () => {
    setup({ pathname: '/login', state: { notice: 'verified' } }, () => json(200, ada))

    expect(await live('status')).toHaveTextContent('Your email is verified. You can log in now.')
  })

  it('submits once while pending', async () => {
    let answer: (response: Response) => void = () => {}
    const { loginCalls } = setup(
      '/login',
      () => new Promise<Response>((resolve) => (answer = resolve)),
    )

    await userEvent.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'a long enough password{Enter}{Enter}')

    await waitFor(() => expect(loginCalls()).toBe(1))
    expect(screen.getByRole('button', { name: 'Log in' })).toHaveAttribute('aria-busy', 'true')
    answer(json(200, ada))
  })

  it('links to register', async () => {
    setup('/login', () => json(200, ada))

    expect(await screen.findByRole('link', { name: 'Register' })).toHaveAttribute(
      'href',
      '/register',
    )
  })

  it('the fields tell password managers what they hold', async () => {
    setup('/login', () => json(200, ada))

    const email = await screen.findByLabelText('Email')
    expect(email).toHaveAttribute('name', 'email')
    expect(email).toHaveAttribute('autocomplete', 'username')
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password')
  })

  it('shows Salem asleep beside the form and no cat next to an error', async () => {
    setup('/login', () => json(401, { title: 'Unauthorized', detail: 'Invalid credentials' }))

    await fillAndSubmit()

    const alert = await live('alert')
    expect(document.querySelector('[data-pose="asleep"]')).not.toBeNull()
    expect(alert.querySelector('[data-pose]')).toBeNull()
    expect(screen.getByRole('main')).toContainElement(alert)
  })
})
