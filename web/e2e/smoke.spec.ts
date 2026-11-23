import { expect, test } from '@playwright/test'

test('an anonymous visitor lands on login', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveURL(/\/login\?next=%2F$/)
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
})
