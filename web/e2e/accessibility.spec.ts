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

      expect(await violations(page)).toEqual([])
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

test('each page names itself in the window title', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle('Log in · Salem Todo DevOps')

  await signIn(page.request)
  await page.goto('/')
  await expect(page).toHaveTitle('All projects · Salem Todo DevOps')
  await page.goto('/no-such-page')
  await expect(page).toHaveTitle('Page not found · Salem Todo DevOps')
})
