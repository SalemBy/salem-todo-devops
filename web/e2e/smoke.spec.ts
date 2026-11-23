import { expect, test } from '@playwright/test'

test('an anonymous visitor lands on login', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveURL(/\/login\?next=%2F$/)
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
})

test('the tab icon is served', async ({ page, request }) => {
  const icon = await request.get('/favicon.svg')
  expect(icon.status()).toBe(200)
  expect(icon.headers()['content-type']).toContain('image/svg+xml')

  await page.goto('/login')
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', '/favicon.svg')
})
