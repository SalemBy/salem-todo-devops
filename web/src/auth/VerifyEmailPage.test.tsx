import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, live, problem, renderApp } from '../test/renderApp'

const INVALID =
  'This link is invalid or has expired, or the password is not the one you registered with.'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('VerifyEmailPage', () => {
  it('verifies and goes to login with a notice', async () => {
    const { router, sent } = renderApp('/verify-email?token=abc', {
      'POST /api/auth/verify': () => json(204),
    })

    await userEvent.type(await screen.findByLabelText('Password'), 'a long enough password')
    await userEvent.click(screen.getByRole('button', { name: 'Verify email' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(await live('status')).toHaveTextContent('Your email is verified. You can log in now.')
    expect(sent('POST /api/auth/verify')[0]?.body).toEqual({
      token: 'abc',
      password: 'a long enough password',
    })
  })

  it('a bad link, an expired link or a wrong password', async () => {
    renderApp('/verify-email?token=abc', {
      'POST /api/auth/verify': () => problem(400, 'Verification link is invalid or has expired'),
    })

    await userEvent.type(await screen.findByLabelText('Password'), 'a long enough password')
    await userEvent.click(screen.getByRole('button', { name: 'Verify email' }))

    expect(await screen.findByText(INVALID)).toBeVisible()
    expect(screen.getByLabelText('Email')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Send a new link' })).toBeVisible()
  })

  it('no token', async () => {
    const { sent } = renderApp('/verify-email', {})

    expect(await screen.findByText(INVALID)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Send a new link' })).toBeVisible()
    expect(screen.queryByLabelText('Password')).toBeNull()
    expect(sent('POST /api/auth/verify')).toHaveLength(0)
  })

  it('sends a new link to the address given', async () => {
    const { sent } = renderApp('/verify-email', {
      'POST /api/auth/verification-email': () => json(202),
    })

    await userEvent.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Send a new link' }))

    expect(await live('status')).toHaveTextContent('Sent. Check your inbox and spam folder.')
    expect(sent('POST /api/auth/verification-email')[0]?.body).toEqual({ email: 'ada@example.com' })
  })

  it('focus moves to the new-link form when the link is refused', async () => {
    renderApp('/verify-email?token=abc', {
      'POST /api/auth/verify': () => problem(400, 'Verification link is invalid or has expired'),
    })

    await userEvent.type(await screen.findByLabelText('Password'), 'a long enough password')
    await userEvent.click(screen.getByRole('button', { name: 'Verify email' }))

    const send = await screen.findByRole('button', { name: 'Send a new link' })
    const half = send.closest('[tabindex="-1"]')
    await waitFor(() => expect(document.activeElement).toBe(half))
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Verify your email')
  })

  it('submits once while pending', async () => {
    const { sent } = renderApp('/verify-email?token=abc', {
      'POST /api/auth/verify': () => new Promise<Response>(() => {}),
    })

    await userEvent.type(
      await screen.findByLabelText('Password'),
      'a long enough password{Enter}{Enter}',
    )

    await waitFor(() => expect(sent('POST /api/auth/verify')).toHaveLength(1))
    expect(screen.getByRole('button', { name: 'Verify email' })).toHaveAttribute(
      'aria-busy',
      'true',
    )
  })

  it('the password field is the current password', async () => {
    renderApp('/verify-email?token=abc', {})

    expect(await screen.findByLabelText('Password')).toHaveAttribute(
      'autocomplete',
      'current-password',
    )
  })

  it('an email the API refuses is marked on the Email field', async () => {
    renderApp('/verify-email', {
      'POST /api/auth/verification-email': () =>
        problem(400, 'Request validation failed', [
          { field: 'email', message: 'must be a well-formed email address' },
        ]),
    })

    await userEvent.type(await screen.findByLabelText('Email'), 'ada@')
    await userEvent.click(screen.getByRole('button', { name: 'Send a new link' }))

    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
        'Enter an email address like name@example.com.',
      ),
    )
    expect(screen.queryByText('Request validation failed')).toBeNull()
  })

  it('a mistyped password can be tried again with the same link', async () => {
    let attempts = 0
    const { router, sent } = renderApp('/verify-email?token=abc', {
      'POST /api/auth/verify': () =>
        ++attempts === 1 ? problem(400, 'Verification link is invalid or has expired') : json(204),
    })

    await userEvent.type(await screen.findByLabelText('Password'), 'a mistyped password{Enter}')
    expect(await screen.findByText(INVALID)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Send a new link' })).toBeVisible()

    await userEvent.clear(screen.getByLabelText('Password'))
    await userEvent.type(screen.getByLabelText('Password'), 'the right password{Enter}')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sent('POST /api/auth/verify').map((r) => r.body)).toEqual([
      { token: 'abc', password: 'a mistyped password' },
      { token: 'abc', password: 'the right password' },
    ])
  })
})
