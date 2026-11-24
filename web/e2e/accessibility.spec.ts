import { AxeBuilder } from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { signIn } from './account.ts'

/** WCAG 2.1 A and AA rules that axe can check automatically. */
async function violations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  return results.violations.map(
    (v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
  )
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`${colorScheme} theme`, () => {
    test.use({ colorScheme })

    test('the login page has no automatic accessibility violations', async ({ page }) => {
      await page.goto('/projects/SALEM/board')
      await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
      await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()

      expect(await violations(page)).toEqual([])
    })

    test('the sign-in screens have no automatic accessibility violations', async ({ page }) => {
      // Answers that would cost a real account or a rate-limit slot are made in the browser.
      await page.route('**/api/auth/register', (route) =>
        route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({ email: 'ada@example.com', displayName: 'Ada Lovelace' }),
        }),
      )
      await page.route('**/api/auth/login', (route) =>
        route.fulfill({
          status: 401,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            title: 'Unauthorized',
            status: 401,
            detail: 'Invalid credentials',
          }),
        }),
      )
      const screens: Record<string, string[]> = {}
      const check = async (name: string) => {
        screens[name] = await violations(page)
      }

      await page.goto('/login')
      await page.getByLabel('Email').fill('ada@example.com')
      await page.getByLabel('Password').fill('not the right password')
      await page.getByRole('button', { name: 'Log in' }).click()
      await expect(page.getByText(/^Invalid email or password/).first()).toBeVisible()
      await check('login refused')

      await page.goto('/register')
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByText('Enter your email.', { exact: true })).toBeVisible()
      await check('register with errors')

      await page.getByLabel('Email').fill('ada@example.com')
      await page.getByLabel('Display name').fill('Ada Lovelace')
      await page.getByLabel('Password').fill('a long enough password')
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()
      await check('check your email')

      await page.goto('/verify-email?token=abc')
      await expect(page.getByLabel('Password')).toBeVisible()
      await check('verify')

      await page.goto('/verify-email')
      await expect(page.getByRole('button', { name: 'Send a new link' })).toBeVisible()
      await check('link refused')

      expect(screens).toEqual({
        'login refused': [],
        'register with errors': [],
        'check your email': [],
        verify: [],
        'link refused': [],
      })
    })

    test('the signed-in shell has no automatic accessibility violations', async ({ page }) => {
      await signIn(page.request, 'Grace Hopper')
      await page.goto('/')
      await expect(page.getByRole('heading', { name: 'All projects' })).toBeVisible()

      expect(await violations(page)).toEqual([])
    })
  })
}

test('the shell reflows at 320 CSS pixels (400% zoom) without sideways scrolling', async ({
  page,
}) => {
  await signIn(page.request)
  await page.setViewportSize({ width: 320, height: 640 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'All projects' })).toBeVisible()

  const main = await page.getByRole('main').boundingBox()
  expect(Number(await page.evaluate('document.documentElement.scrollWidth'))).toBeLessThanOrEqual(
    320,
  )
  expect(main?.width ?? 0).toBeGreaterThanOrEqual(280)
})

test('the login page reflows at 320 CSS pixels with the form first', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 640 })
  await page.goto('/login')
  const email = page.getByRole('textbox', { name: 'Email' })
  await expect(email).toBeVisible()

  expect(Number(await page.evaluate('document.documentElement.scrollWidth'))).toBeLessThanOrEqual(
    320,
  )
  const box = await email.boundingBox()
  expect((box?.y ?? Infinity) + (box?.height ?? 0)).toBeLessThanOrEqual(640)
})

test('each page names itself in the window title', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle('Log in · Salem Todo DevOps')

  await signIn(page.request)
  await page.goto('/')
  await expect(page).toHaveTitle('All projects · Salem Todo DevOps')
  await page.goto('/no-such-page')
  await expect(page).toHaveTitle('Page not found · Salem Todo DevOps')
})
