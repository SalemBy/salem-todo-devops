import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, live, problem, renderApp } from '../test/renderApp'

const created = {
  id: 7,
  email: 'ada@example.com',
  displayName: 'Ada Lovelace',
  active: true,
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
  emailVerified: false,
}

async function fillAndSubmit() {
  await userEvent.type(await screen.findByLabelText('Email'), 'ada@example.com')
  await userEvent.type(screen.getByLabelText('Display name'), 'Ada Lovelace')
  await userEvent.type(screen.getByLabelText('Password'), 'a long enough password')
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('RegisterPage', () => {
  it('shows the password rule', async () => {
    renderApp('/register', {})

    expect(await screen.findByLabelText('Password')).toHaveAccessibleDescription(
      '15 to 128 characters.',
    )
  })

  it('sends the registration', async () => {
    const { sent } = renderApp('/register', { 'POST /api/auth/register': () => json(201, created) })

    await fillAndSubmit()

    await screen.findByRole('heading', { name: 'Check your email' })
    expect(sent('POST /api/auth/register')[0]?.body).toEqual({
      email: 'ada@example.com',
      displayName: 'Ada Lovelace',
      password: 'a long enough password',
    })
  })

  it('field errors land on their fields', async () => {
    renderApp('/register', {
      'POST /api/auth/register': () =>
        problem(400, 'Request validation failed', [
          { field: 'email', message: 'must be a well-formed email address' },
          {
            field: 'passwordLengthValid',
            message: 'Password must contain 15 to 128 Unicode code points',
          },
        ]),
    })

    await fillAndSubmit()

    const password = await screen.findByLabelText('Password')
    await waitFor(() => expect(password).toHaveAttribute('aria-invalid', 'true'))
    // The server's wording (and language) is replaced by the page's own plain copy.
    expect(password).toHaveAccessibleDescription('Use 15 to 128 characters.')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter an email address like name@example.com.',
    )
  })

  it('an email already registered', async () => {
    renderApp('/register', {
      'POST /api/auth/register': () => problem(409, 'Email is already registered'),
    })

    await fillAndSubmit()

    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
        'This email is already registered.',
      ),
    )
    expect(screen.getAllByRole('link', { name: 'Log in' })[0]).toHaveAttribute('href', '/login')
  })

  it('registration closed', async () => {
    renderApp('/register', {
      'POST /api/auth/register': () => problem(403, 'Registration is disabled'),
    })

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Registration is closed' })).toBeVisible()
    expect(screen.getByText('Ask the person who runs this workspace for access.')).toBeVisible()
    expect(screen.queryByLabelText('Display name')).toBeNull()
  })

  it('moves focus to the new content when the page changes', async () => {
    renderApp('/register', { 'POST /api/auth/register': () => json(201, created) })

    await fillAndSubmit()

    const heading = await screen.findByRole('heading', { name: 'Check your email' })
    await waitFor(() => expect(document.activeElement).toBe(heading.closest('[tabindex="-1"]')))
    expect(screen.queryByRole('button', { name: 'Create account' })).toBeNull()
  })

  it('explains the display name', async () => {
    renderApp('/register', {})

    expect(await screen.findByLabelText('Display name')).toHaveAccessibleDescription(
      'Shown to the people in your projects.',
    )
  })

  it('201 shows check your email with Salem asleep', async () => {
    renderApp('/register', { 'POST /api/auth/register': () => json(201, created) })

    await fillAndSubmit()

    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeVisible()
    expect(screen.getByText(/ada@example\.com/)).toBeVisible()
    const cat = document.querySelector('svg[data-pose="asleep"]')
    expect(cat?.closest('[aria-hidden="true"]')).not.toBeNull()
    expect(screen.getByRole('button', { name: 'Send it again' })).toBeVisible()
  })

  it('send it again', async () => {
    const { sent } = renderApp('/register', {
      'POST /api/auth/register': () => json(201, created),
      'POST /api/auth/verification-email': () => json(202),
    })
    await fillAndSubmit()

    await userEvent.click(await screen.findByRole('button', { name: 'Send it again' }))

    expect(await live('status')).toHaveTextContent('Sent. Check your inbox and spam folder.')
    expect(sent('POST /api/auth/verification-email')[0]?.body).toEqual({ email: 'ada@example.com' })
  })

  it('send it again too often says when to retry', async () => {
    renderApp('/register', {
      'POST /api/auth/register': () => json(201, created),
      'POST /api/auth/verification-email': () =>
        problem(429, 'Too many requests. Try again later.', [], { 'Retry-After': '120' }),
    })
    await fillAndSubmit()

    await userEvent.click(await screen.findByRole('button', { name: 'Send it again' }))

    expect(await live('alert')).toHaveTextContent('Try again in 2 minutes.')
  })

  it('the fields tell password managers what they hold', async () => {
    renderApp('/register', {})

    expect(await screen.findByLabelText('Email')).toHaveAttribute('autocomplete', 'email')
    expect(screen.getByLabelText('Display name')).toHaveAttribute('autocomplete', 'name')
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'new-password')
  })

  it('submits once while pending', async () => {
    const { sent } = renderApp('/register', {
      'POST /api/auth/register': () => new Promise<Response>(() => {}),
    })

    await userEvent.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await userEvent.type(screen.getByLabelText('Display name'), 'Ada Lovelace')
    await userEvent.type(screen.getByLabelText('Password'), 'a long enough password{Enter}{Enter}')

    await waitFor(() => expect(sent('POST /api/auth/register')).toHaveLength(1))
    expect(screen.getByRole('button', { name: 'Create account' })).toHaveAttribute(
      'aria-busy',
      'true',
    )
  })
})
