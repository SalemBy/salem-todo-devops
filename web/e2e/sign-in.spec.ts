import { expect, test } from '@playwright/test'
import { csrf, letters, signIn } from './account.ts'
import { verificationToken } from './mailpit.ts'

const password = 'e2e correct horse battery staple'
const newEmail = () => `e2e-${Date.now()}-${letters().toLowerCase()}@example.com`

test('register, verify, log in and log out in the browser', async ({ page }) => {
  const email = newEmail()

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Display name').fill('E2E Tester')
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()

  await page.goto(`/verify-email?token=${await verificationToken(email)}`)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Verify email' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(
    page.getByRole('status').filter({ hasText: 'Your email is verified. You can log in now.' }),
  ).toBeVisible()

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { name: 'All projects' })).toBeVisible()

  await page.getByRole('button', { name: 'Account: E2E Tester' }).click()
  await page.getByRole('menuitem', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status').filter({ hasText: 'You are logged out.' })).toBeVisible()
})

test('an unverified account gets the generic login error', async ({ page, request }) => {
  const email = newEmail()
  const registered = await request.post('/api/auth/register', {
    headers: await csrf(request),
    data: { email, displayName: 'Not Verified', password },
  })
  expect(registered.status()).toBe(201)

  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page.getByRole('alert')).toHaveText(
    'Invalid email or password. If you registered recently, open the link in your verification email first.',
  )
})

test('after logging in, an expired session returns to the page it was on', async ({ page }) => {
  // A fresh verified account, signed in through the browser's own request client afterwards.
  const account = await signIn(page.request)
  await page.request.post('/api/auth/logout', { headers: await csrf(page.request) })

  await page.goto('/no-such-page?from=e2e')
  await expect(page).toHaveURL(/\/login\?next=/)
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(account.password)
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page).toHaveURL(/\/no-such-page\?from=e2e$/)
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()
})
